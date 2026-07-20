package dev.merqadyn.api

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class MerqadynApiApplication

fun main(args: Array<String>) {
	runApplication<MerqadynApiApplication>(*args)
}
