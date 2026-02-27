
# Group Code Standards

From https://kotlinlang.org/docs/coding-conventions.html

These rules keep our code consistent, readable, and easy to maintain.

---

## IDE Setup (Required)
- Use IntelliJ / Android Studio Kotlin style guide  
  `Settings → Editor → Code Style → Kotlin → Set from… → Kotlin style guide`
- Auto-format before committing

---

## Project Structure
- Directory structure must match package names
- Packages: lowercase, no underscores
- File name = main class name (`UserService.kt`)
- Avoid vague names like `Util`, `Helper`

---

## Naming
- Classes / Objects: `UpperCamelCase`
- Functions / variables: `lowerCamelCase`
- Constants: `SCREAMING_SNAKE_CASE`
- Prefer clear, descriptive names

---

## Formatting
- 4 spaces indentation (no tabs)
- No semicolons
- Opening `{` on the same line
- Wrap long lines instead of cramming

---

## Classes & Files
- Keep files small and focused
- Related code stays together
- Order inside classes:
  1. Properties
  2. Constructors
  3. Functions
  4. Companion object

---

## Immutability
- Prefer `val` over `var`
- Use immutable collections (`List`, `Set`, `Map`)
- Avoid mutable collections unless required

---

## Functions
- Prefer block bodies:
  ```kotlin
  fun sum(a: Int, b: Int){
	 a + b
  }
  Omit : Unit```

Use default parameters instead of overloads
s
