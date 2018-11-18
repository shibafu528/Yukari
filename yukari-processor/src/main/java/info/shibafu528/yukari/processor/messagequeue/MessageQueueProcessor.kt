package info.shibafu528.yukari.processor.messagequeue

import com.squareup.javapoet.AnnotationSpec
import com.squareup.javapoet.ClassName
import com.squareup.javapoet.FieldSpec
import com.squareup.javapoet.JavaFile
import com.squareup.javapoet.MethodSpec
import com.squareup.javapoet.ParameterSpec
import com.squareup.javapoet.TypeName
import com.squareup.javapoet.TypeSpec
import com.squareup.javapoet.TypeVariableName
import java.util.*
import java.util.concurrent.BlockingQueue
import java.util.concurrent.LinkedBlockingQueue
import javax.annotation.processing.AbstractProcessor
import javax.annotation.processing.RoundEnvironment
import javax.annotation.processing.SupportedSourceVersion
import javax.lang.model.SourceVersion
import javax.lang.model.element.ElementKind
import javax.lang.model.element.Modifier
import javax.lang.model.element.TypeElement
import javax.lang.model.util.ElementFilter
import javax.tools.Diagnostic

@SupportedSourceVersion(SourceVersion.RELEASE_8)
class MessageQueueProcessor : AbstractProcessor() {
    override fun process(annotations: Set<TypeElement>, roundEnv: RoundEnvironment): Boolean {
        roundEnv.getElementsAnnotatedWith(MessageQueue::class.java)
                .forEach {
                    if (it.kind != ElementKind.INTERFACE) {
                        processingEnv.messager.printMessage(Diagnostic.Kind.ERROR, "${it.simpleName} is not interface.", it)
                        return@forEach
                    }
                    val targetClass = it as? TypeElement ?: return@forEach
                    val packageName = processingEnv.elementUtils.getPackageOf(targetClass).qualifiedName.toString()
                    val annotation = targetClass.getAnnotation(MessageQueue::class.java)
                    val queueClassName = if (annotation.queueClass.isEmpty()) "${targetClass.simpleName}Queue" else annotation.queueClass
                    val queueClassBuilder = TypeSpec.classBuilder(queueClassName)
                            .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
                            .addSuperinterface(TypeName.get(targetClass.asType()))

                    // Generate fields
                    val handlerFieldSpec = FieldSpec.builder(TypeName.get(targetClass.asType()), "handler", Modifier.PRIVATE).build()
                    queueClassBuilder.addField(handlerFieldSpec)
                    val queueFieldSpec = FieldSpec.builder(ClassName.get(BlockingQueue::class.java), "queue", Modifier.PRIVATE)
                            .initializer("new \$T()", ClassName.get(LinkedBlockingQueue::class.java))
                            .build()
                    queueClassBuilder.addField(queueFieldSpec)
                    val threadFieldSpec = FieldSpec.builder(ClassName.get(Thread::class.java), "thread", Modifier.PRIVATE).build()
                    queueClassBuilder.addField(threadFieldSpec)

                    // Generate interface implements
                    val proceedMethodList = mutableListOf<Pair<String, TypeSpec>>()
                    ElementFilter.methodsIn(targetClass.enclosedElements).forEach { member ->
                        val returnType = TypeName.get(member.returnType)
                        if (returnType != TypeName.VOID) {
                            processingEnv.messager.printMessage(Diagnostic.Kind.ERROR, "${member.simpleName} is not void return.", member)
                        }

                        val methodBuilder = MethodSpec.methodBuilder(member.simpleName.toString())
                                .addModifiers(Modifier.PUBLIC)
                                .returns(returnType)

                        val messageFields = mutableListOf<Pair<TypeName, String>>()

                        // メソッドシグネチャを写す
                        member.parameters.forEach { param ->
                            val paramSpec = ParameterSpec.builder(TypeName.get(param.asType()), param.simpleName.toString(), *param.modifiers.toTypedArray())
                                    .addAnnotations(param.annotationMirrors.map(AnnotationSpec::get))
                                    .build()
                            methodBuilder.addParameter(paramSpec)
                            messageFields += TypeName.get(param.asType()) to param.simpleName.toString()
                        }
                        member.typeParameters.forEach { typeParam ->
                            methodBuilder.addTypeVariable(TypeVariableName.get(typeParam.simpleName.toString(), *typeParam.bounds.map(TypeName::get).toTypedArray()))
                        }
                        member.thrownTypes.forEach { thrown ->
                            methodBuilder.addException(TypeName.get(thrown))
                        }

                        // キュー処理の対象かどうか判定
                        if (member.getAnnotation(PassThrough::class.java) == null) {
                            // 対象
                            // パラメータ格納クラスを生成
                            val messageClassSpec = TypeSpec.classBuilder("Message_${member.simpleName}")
                                    .addModifiers(Modifier.PRIVATE, Modifier.STATIC)
                                    .superclass(ClassName.get(Message::class.java))
                                    .apply {
                                        messageFields.forEach { (type, name) ->
                                            addField(type, name)
                                        }
                                    }
                                    .build()
                            queueClassBuilder.addType(messageClassSpec)

                            // 実装を投入
                            methodBuilder.addStatement("\$N msg = new \$N()", messageClassSpec, messageClassSpec)
                                    .addStatement("msg.methodId = \$L", proceedMethodList.size)
                                    .apply {
                                        member.parameters.forEach { param ->
                                            addStatement("msg.\$L = \$L", param.simpleName.toString(), param.simpleName.toString())
                                        }
                                    }
                                    .addStatement("\$N.offer(msg)", queueFieldSpec)

                            proceedMethodList.add(member.simpleName.toString() to messageClassSpec)
                        } else {
                            // 対象外
                            // 実装を投入
                            val placeholders = buildString {
                                messageFields.forEach { _ ->
                                    if (this.isNotEmpty()) append(",")
                                    append("\$L")
                                }
                            }
                            methodBuilder.addStatement("\$N.\$L($placeholders)", handlerFieldSpec, member.simpleName.toString(), *messageFields.map { f -> f.second }.toTypedArray())
                        }

                        // メソッドを登録
                        queueClassBuilder.addMethod(methodBuilder.build())
                    }

                    // Generate inner worker
                    val dispatchMethodSpec = MethodSpec.methodBuilder("dispatch")
                            .addModifiers(Modifier.PRIVATE)
                            .addParameter(ClassName.get(Message::class.java), "msg")
                            .returns(TypeName.VOID)
                            .beginControlFlow("switch (msg.methodId)")
                            .apply {
                                proceedMethodList.forEachIndexed { index, (methodName, messageClassSpec) ->
                                    addCode("case \$L: \n", index)
                                    addCode("{\n")
                                    addStatement("\$N tmsg = (\$N) msg", messageClassSpec, messageClassSpec)

                                    val placeholders = buildString {
                                        messageClassSpec.fieldSpecs.forEach {
                                            if (this.isNotEmpty()) append(",")
                                            append("tmsg.\$N")
                                        }
                                    }
                                    addStatement("\$N.\$L($placeholders)", handlerFieldSpec, methodName, *messageClassSpec.fieldSpecs.toTypedArray())
                                    addCode("}\n")
                                    addStatement("break")
                                }
                            }
                            .endControlFlow()
                            .build()
                    val workerClassSpec = TypeSpec.classBuilder("Worker")
                            .addModifiers(Modifier.PRIVATE)
                            .addSuperinterface(ClassName.get(Runnable::class.java))
                            .addMethod(MethodSpec.methodBuilder("run")
                                    .addModifiers(Modifier.PUBLIC)
                                    .returns(TypeName.VOID)
                                    .beginControlFlow("while (true)")
                                    .addStatement("\$T msg", ClassName.get(Message::class.java))
                                    .beginControlFlow("try")
                                    .addStatement("msg = (\$T) \$N.take()", ClassName.get(Message::class.java), queueFieldSpec)
                                    .nextControlFlow("catch (\$T e)", ClassName.get(InterruptedException::class.java))
                                    .addStatement("break")
                                    .endControlFlow()
                                    .addStatement("dispatch(msg)")
                                    .endControlFlow()
                                    .build())
                            .addMethod(dispatchMethodSpec)
                            .build()
                    queueClassBuilder.addType(workerClassSpec)

                    // Generate constructor
                    val handlerParamSpec = ParameterSpec.builder(TypeName.get(targetClass.asType()), "handler")
                            .addAnnotation(javax.annotation.Nonnull::class.java)
                            .build()
                    val constructor = MethodSpec.constructorBuilder()
                            .addModifiers(Modifier.PUBLIC)
                            .addParameter(handlerParamSpec)
                            .addStatement("this.\$N = \$N", handlerFieldSpec, handlerParamSpec)
                            .addStatement("this.\$N = new \$T(new \$N(), \$S)", threadFieldSpec, ClassName.get(Thread::class.java), workerClassSpec, "${queueClassName}Worker")
                            .addStatement("this.\$N.start()", threadFieldSpec)
                            .build()
                    queueClassBuilder.addMethod(constructor)

                    JavaFile.builder(packageName, queueClassBuilder.build())
                            .build()
                            .writeTo(processingEnv.filer)
                }
        return true
    }

    override fun getSupportedAnnotationTypes(): Set<String> {
        val types = HashSet<String>()

        types.add(MessageQueue::class.java.canonicalName)

        return types
    }
}
