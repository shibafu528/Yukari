package info.shibafu528.yukari.processor.filter

import com.squareup.javapoet.ClassName
import com.squareup.javapoet.CodeBlock
import com.squareup.javapoet.FieldSpec
import com.squareup.javapoet.JavaFile
import com.squareup.javapoet.ParameterizedTypeName
import com.squareup.javapoet.TypeName
import com.squareup.javapoet.TypeSpec
import com.squareup.javapoet.WildcardTypeName
import javax.annotation.processing.AbstractProcessor
import javax.annotation.processing.RoundEnvironment
import javax.annotation.processing.SupportedSourceVersion
import javax.lang.model.SourceVersion
import javax.lang.model.element.Modifier
import javax.lang.model.element.TypeElement
import javax.tools.Diagnostic

@SupportedSourceVersion(SourceVersion.RELEASE_8)
class SourceProcessor : AbstractProcessor() {
    private val sources = HashMap<String, MutableMap<Int, String>>()

    override fun process(annotations: MutableSet<out TypeElement>, roundEnv: RoundEnvironment): Boolean {
        val filterSourceType = processingEnv.elementUtils.getTypeElement("shibafu.yukari.filter.source.FilterSource")

        // アノテーション使用部分を検索してマップを作る
        roundEnv.getElementsAnnotatedWith(Source::class.java)
                .forEach { element ->
                    if (element !is TypeElement) {
                        return@forEach
                    }

                    if (!processingEnv.typeUtils.isAssignable(element.asType(), filterSourceType.asType())) {
                        processingEnv.messager.printMessage(Diagnostic.Kind.ERROR, "${element.simpleName} was not implemented FilterSource.", element)
                        return@forEach
                    }

                    val annotation = element.getAnnotation(Source::class.java)

                    val providerToClass = sources.getOrPut(annotation.slug) { HashMap() }
                    providerToClass[annotation.apiType] = element.qualifiedName.toString()
                }

        // 最終ラウンドでコード生成
        if (roundEnv.processingOver()) {
            // TODO: debug message
            sources.forEach { slug, providerToClass ->
                processingEnv.messager.printMessage(Diagnostic.Kind.WARNING, "Slug: $slug")
                providerToClass.forEach { apiType, className ->
                    processingEnv.messager.printMessage(Diagnostic.Kind.WARNING, "    API: $apiType, $className")
                }
            }

            // マップを定数として書き出す
            val classBuilder = TypeSpec.classBuilder("Sources")
                    .addModifiers(Modifier.FINAL)

            val mapType = ClassName.get(java.util.Map::class.java)
            val hashMapType = ClassName.get(java.util.HashMap::class.java)
            val boxedIntegerType = TypeName.INT.box()
            val stringType = ClassName.get(java.lang.String::class.java)
            val innerMapTypeArgs = arrayOf(boxedIntegerType, ParameterizedTypeName.get(ClassName.get(Class::class.java), WildcardTypeName.subtypeOf(TypeName.get(filterSourceType.asType()))))
            val innerMapType = ParameterizedTypeName.get(mapType, *innerMapTypeArgs)
            val outerMapTypeArgs = arrayOf(stringType, innerMapType)
            val outerMapType = ParameterizedTypeName.get(mapType, *outerMapTypeArgs)
            val mapFieldSpec = FieldSpec.builder(outerMapType, "MAP", Modifier.STATIC, Modifier.FINAL).build()

            val staticInitializerBlock = CodeBlock.builder().apply {
                addStatement("\$N = new \$T()", mapFieldSpec, ParameterizedTypeName.get(hashMapType, *outerMapTypeArgs))

                sources.forEach { sourceSlug, providerToClass ->
                    add("{\n")
                    indent()

                    addStatement("\$T providerToClass = new \$T()", innerMapType, ParameterizedTypeName.get(hashMapType, *innerMapTypeArgs))

                    providerToClass.forEach { apiType, className ->
                        addStatement("providerToClass.put(\$L, \$L.class)", apiType, className)
                    }

                    addStatement("\$N.put(\$S, providerToClass)", mapFieldSpec, sourceSlug)

                    unindent()
                    add("}\n")
                }
            }.build()

            classBuilder.addField(mapFieldSpec)
                    .addStaticBlock(staticInitializerBlock)

            JavaFile.builder("shibafu.yukari.filter.compiler", classBuilder.build())
                    .build()
                    .writeTo(processingEnv.filer)
        }

        return true
    }

    override fun getSupportedAnnotationTypes(): MutableSet<String> {
        val types = HashSet<String>()

        types.add(Source::class.java.canonicalName)

        return types
    }
}