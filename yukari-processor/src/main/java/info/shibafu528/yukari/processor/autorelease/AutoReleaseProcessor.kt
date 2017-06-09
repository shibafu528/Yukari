package info.shibafu528.yukari.processor.autorelease

import com.squareup.javapoet.ClassName
import com.squareup.javapoet.JavaFile
import com.squareup.javapoet.MethodSpec
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
class AutoReleaseProcessor : AbstractProcessor() {
    override fun process(annotations: Set<TypeElement>, roundEnv: RoundEnvironment): Boolean {
        roundEnv.getElementsAnnotatedWith(AutoRelease::class.java)
                .groupBy { it.enclosingElement as TypeElement }
                .forEach {
                    val (key, value) = it

                    val packageName = processingEnv.elementUtils.getPackageOf(key).qualifiedName.toString()
                    val targetClass = ClassName.get(key)

                    val releaseMethod = MethodSpec.methodBuilder("release")
                            .addParameter(targetClass, "target")
                            .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
                            .apply {
                                value.forEach {
                                    if (it.modifiers.contains(Modifier.PRIVATE)) {
                                        processingEnv.messager.printMessage(Diagnostic.Kind.ERROR, "AutoReleaseの対象をprivateフィールドにすることは出来ません。", it)
                                    }
                                    addStatement("target.${it.simpleName} = null")
                                }
                            }
                            .build()

                    val autoRelease = TypeSpec.classBuilder(key.simpleName.toString() + "\$AutoRelease")
                            .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
                            .addMethod(releaseMethod)
                            .build()

                    JavaFile.builder(packageName, autoRelease)
                            .build()
                            .writeTo(processingEnv.filer)
                }

        return true
    }

    override fun getSupportedAnnotationTypes(): Set<String> {
        val types = HashSet<String>()

        types.add(AutoRelease::class.java.canonicalName)

        return types
    }
}
