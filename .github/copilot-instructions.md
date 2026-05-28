---
description: AI rules derived by SpecStory from the project AI interaction history
applyTo: *
---

## PROJECT OVERVIEW
- The AI coding assistant helps users with coding tasks, project setup, and understanding existing codebases.
- It follows project rules, coding standards, and workflow guidelines defined in this document.
- The primary goal is to deliver accurate, efficient, and helpful assistance while adhering to established project conventions.

## CODE STYLE
- Adhere to the code style conventions defined in the project.
- Use consistent formatting, naming conventions, and commenting practices.
- Follow language-specific best practices and style guides (e.g., Kotlin style guide for Kotlin code).

## FOLDER ORGANIZATION
- Maintain the project's folder structure.
- Place new files and code in the appropriate directories.
- Do not create unnecessary or redundant folders.

## TECH STACK
- Java
- Kotlin
- Gradle (Kotlin DSL)
- Media3 (Custom Build)
- Room
- Hilt
- Ktor
- Coil
- JDK 17

## PROJECT-SPECIFIC STANDARDS
- OuterTune's code standards should be followed unless explicitly overridden by project-specific rules.
- When working with UI components, adhere to the Material 3 design principles.
- When constructing file paths, use links like `[file](path#L10)` instead of backticks.
- Application ID: `com.nohimazin.monotune`
- Minimum SDK: 24 (Android 7.0+)
- R/BuildConfig namespace: `com.nohimazin.monotune`
- strings.xml `app_name`: `MonoTune`

## WORKFLOW & RELEASE RULES
- All code changes must be reviewed and approved before being merged.
- Follow the established Git branching strategy.
- Copilot tasks should be tracked using the specified task URL.

## REFERENCE EXAMPLES
- Refer to existing code and documentation for guidance on implementing new features or making changes.
- Use the provided code snippets and examples as a starting point for your work.

## PROJECT DOCUMENTATION & CONTEXT SYSTEM
- This file serves as the primary source of truth for project rules and guidelines.
- Refer to external documentation and resources as needed, but prioritize the information in this file.
- BRANCHES.md contains additional information for project progress.

## DEBUGGING
- Use logging and debugging tools to identify and fix errors.
- Follow established debugging practices and procedures.

## FINAL DOs AND DON'Ts
- **DO** Retain the structure of the AI coding assistant rules file.
- **DO** Read the new user–AI interactions carefully.
- **DO** Look for signals that the user's intent is to make something a permanent rule.
- **DON'T** Make ANY adjustments to the AI coding assistant rules file unless you are absolutely sure that the user's intent is rule like in nature.
- **DON'T** use backticks for filenames - use links like `[file](path#L10)` instead.
