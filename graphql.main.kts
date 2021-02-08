#!/usr/bin/env kotlin

/* MavenCenter */
@file:Repository("https://repo1.maven.org/maven2/")
@file:DependsOn("com.github.kittinunf.fuel:fuel:2.3.0")
@file:DependsOn("info.picocli:picocli:4.5.1")

import com.github.kittinunf.fuel.Fuel
import com.github.kittinunf.fuel.core.extensions.*
import java.util.concurrent.Callable
import kotlin.system.exitProcess
import picocli.CommandLine

@CommandLine.Command(
    name = "GithubGraphQL",
    mixinStandardHelpOptions = false,
    version = ["1.0.0"],
    description = ["Github GraphQL -- Playground"]
)
class GithubGraphQL : Callable<Int> {
    @CommandLine.Parameters(index = "0", description = ["Github token"])
    lateinit var token: String

    @CommandLine.Parameters(
      index = "1",
      description = ["GraphQL request"],
      defaultValue = "{ \"query\": \"query { viewer { login } }\" }"
    )
    lateinit var request: String 

    @Throws(Exception::class)
    override fun call(): Int {
        val (_, _, result) = Fuel.post("https://api.github.com/graphql")
            .authentication().bearer(token)
            .jsonBody(request)
            .responseString()
        result.fold({ println(it) },{ error -> println("Error: \n$error\n") })      
        return 0
    }
}

val exitCode = CommandLine(GithubGraphQL()).execute(*args)
exitProcess(exitCode)