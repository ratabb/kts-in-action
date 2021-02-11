#!/usr/bin/env kotlin

/* MavenCenter */
@file:Repository("https://repo1.maven.org/maven2/")
@file:DependsOn("org.jetbrains.kotlin:kotlin-stdlib:1.4.30")
@file:DependsOn("io.nayuki:qrcodegen:1.6.0")
@file:DependsOn("com.github.ajalt.clikt:clikt-jvm:3.1.0")
@file:CompilerOptions("-jvm-target", "1.8")

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.options.check
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.versionOption
import com.github.ajalt.clikt.parameters.types.enum
import com.github.ajalt.clikt.parameters.types.int
import io.nayuki.qrcodegen.QrCode.Ecc
import io.nayuki.qrcodegen.QrCode.encodeSegments
import io.nayuki.qrcodegen.QrCode.MAX_VERSION
import io.nayuki.qrcodegen.QrCode.MIN_VERSION
import io.nayuki.qrcodegen.QrSegment.makeSegments
import java.awt.image.BufferedImage
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.util.Locale
import javax.imageio.ImageIO

enum class Type {
    PNG, SVG;

    val ext: String
        get() = name.toLowerCase(Locale.ROOT)
}

fun BufferedImage.writePng(filePath: String) {
    ImageIO.write(this, "png", File(filePath))
}

fun String.writeSvg(filePath: String) {
    Files.write(File(filePath).toPath(), this.toByteArray())
}

fun exportGihubEnv(name: String, value: String){
    File(System.getenv("GITHUB_ENV")).appendText("${name}=${value}\n")
}

class QrCodeGenCli : CliktCommand(
    name = "QrCodeGenCli",
    epilog = "CopyLeft -- 2021\nbase on https://github.com/nayuki/QR-Code-generator",
    printHelpOnEmptyArgs = true
) {
    init {
        versionOption("0.0.1")
    }

    private val text: String by argument(help = "Text to be encode")

    private val ecc: Ecc by option(
        help = "Error correction level"
    ).enum<Ecc>().default(Ecc.MEDIUM)

    private val type: Type by option(
        help = "Output type"
    ).enum<Type>().default(Type.SVG)

    private val scale: Int by option(
        help = "The side length (measured in pixels, must be positive) of each module"
    ).int().default(5).check("Should be a positive number") { it > 0 }

    private val border: Int by option(
        help = "The number of border modules to add, which must be non-negative"
    ).int().default(2).check("Should be a positive number") { it >= 0 }

    private val mask: Int by option(
        help = "The mask number to use (between 0 and 7 (inclusive)), or -1 for automatic mask"
    ).int().default(-1)
        .check("Should be number between 0 and 7 (inclusive) OR -1 (auto)") { it >= -1 && it <= 7 }

    private val output: String by option(
        help = "Output file name without extension"
    ).default("output").check("Should be not existed file") {
        File("$it.${type.ext}").exists().not()
    }

    private val boostEcl: Boolean by option("--boost", hidden = true)
        .flag("--no-boost", default = true)

    private val verbose: Boolean by option(hidden = true).flag(default = false)
    override fun toString(): String = "QrCodeGenCli[text: $text, ecc: ${ecc.name}, " +
        "type: ${type.name}, scale: $scale, border: $border, mask: $mask, output: $output, " +
        "boostEcl: $boostEcl]"

    override fun run() {
        if (verbose) echo(">>> ${toString()} <<<")
        try {
            val code = encodeSegments(
                makeSegments(text),
                ecc,
                MIN_VERSION,
                MAX_VERSION,
                mask,
                boostEcl
            )
            val file = "$output.${type.ext}"
            when (type) {
                Type.PNG -> code.toImage(scale, border).writePng(file)
                Type.SVG -> code.toSvgString(border).writeSvg(file)
            }
            exportGihubEnv("qrOutput", file)
        } catch (e: Exception) {
            echo(e.localizedMessage, err = true)
        }
    }
}

QrCodeGenCli().main(args)
