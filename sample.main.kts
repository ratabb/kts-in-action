#!/usr/bin/env kotlin

 val name: String = args.firstOrNull() ?: "stranger"

println("Hello, $name!")