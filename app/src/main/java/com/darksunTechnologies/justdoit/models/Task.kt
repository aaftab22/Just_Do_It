package com.darksunTechnologies.justdoit.models

class Task {
        var name: String
        var isHighPriority: Boolean

        constructor(name: String, isHighPriority: Boolean) {
            this.name = name
            this.isHighPriority = isHighPriority
        }

        override fun toString(): String {
            return "Task(name='$name', isHighPriority=$isHighPriority)"
        }
}