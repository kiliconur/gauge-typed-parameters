package com.intellij.openapi.components

import com.intellij.openapi.project.Project

@Suppress("UNCHECKED_CAST")
inline fun <reified T : Any> Project.service(): T = null as T
