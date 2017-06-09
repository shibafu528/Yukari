package info.shibafu528.yukari.processor.database

import com.squareup.javapoet.ClassName
import com.squareup.javapoet.JavaFile
import com.squareup.javapoet.MethodSpec
import com.squareup.javapoet.TypeName
import com.squareup.javapoet.TypeSpec
import java.util.*
import javax.annotation.processing.AbstractProcessor
import javax.annotation.processing.RoundEnvironment
import javax.annotation.processing.SupportedSourceVersion
import javax.lang.model.SourceVersion
import javax.lang.model.element.Modifier
import javax.lang.model.element.TypeElement
import javax.tools.Diagnostic

@SupportedSourceVersion(SourceVersion.RELEASE_8)
class ContentValuesProcessor : AbstractProcessor() {
    override fun process(annotations: Set<TypeElement>, roundEnv: RoundEnvironment): Boolean {
        roundEnv.getElementsAnnotatedWith(DBColumn::class.java)
                .groupBy { it.enclosingElement as TypeElement }
                .forEach {
                    val (key, value) = it

                    val packageName = processingEnv.elementUtils.getPackageOf(key).qualifiedName.toString()
                    val targetClass = ClassName.get(key)

                    val contentValuesClass = ClassName.get("android.content", "ContentValues")
                    val getContentValuesMethod = MethodSpec.methodBuilder("getContentValues")
                            .addParameter(targetClass, "target")
                            .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
                            .addStatement("\$T values = new \$T()", contentValuesClass)
                            .apply {
                                value.forEach {
                                    if (it.modifiers.contains(Modifier.PRIVATE)) {
                                        processingEnv.messager.printMessage(Diagnostic.Kind.ERROR, "DBColumnの対象をprivateフィールドにすることは出来ません。", it)
                                    }
                                    val annotationValue = it.getAnnotation(DBColumn::class.java).value

                                    val type = TypeName.get(it.asType())
                                    when (type) {
                                        ClassName.get("java.util", "Date") -> {
                                            addStatement("values.put(\$S, target.${it.simpleName}.getTime())", annotationValue)
                                        }
                                        ClassName.get("java.lang", "String") -> {
                                            addStatement("values.put(\$S, target.${it.simpleName})", annotationValue)
                                        }
                                        TypeName.LONG -> {
                                            if (it.simpleName.toString() == "id") {
                                                beginControlFlow("if (id != -1)")
                                                addStatement("values.put(\$S, target.id)", "id")
                                                endControlFlow()
                                            } else {
                                                addStatement("values.put(\$S, target.${it.simpleName})", annotationValue)
                                            }
                                        }
                                        TypeName.SHORT, TypeName.INT, TypeName.FLOAT, TypeName.DOUBLE, TypeName.BOOLEAN, TypeName.BYTE -> {
                                            addStatement("values.put(\$S, target.${it.simpleName})", annotationValue)
                                        }
                                        else -> {
                                            processingEnv.messager.printMessage(Diagnostic.Kind.ERROR, "格納不能な型のフィールドです。(Boxingとかだったらすまん、直して)", it)
                                        }
                                    }
                                }
                            }
                            .addStatement("return values")
                            .returns(contentValuesClass)
                            .build()

                    val contentValues = TypeSpec.classBuilder(key.simpleName.toString() + "\$ContentValues")
                            .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
                            .addMethod(getContentValuesMethod)
                            .build()

                    JavaFile.builder(packageName, contentValues)
                            .build()
                            .writeTo(processingEnv.filer)
                }

        return true
    }

    override fun getSupportedAnnotationTypes(): Set<String> {
        val types = HashSet<String>()

        types.add(DBColumn::class.java.canonicalName)

        return types
    }
}
