# Contributing to Shollu Android

Thank you for your interest in contributing to Shollu Android! We welcome issues, suggestions, and pull requests to help keep Islamic prayer times accurate, lightweight, and accessible.

## Codebase Principles
This project adheres to **Deep Module Design Principles** (`codebase-design`):
- **Domain Independence**: Pure domain calculations (`engine/`) must remain free of Android framework dependencies (`Context`, `R.string`, etc.).
- **Minimal Interface Surface**: Repositories and engines expose small, clear interfaces and encapsulate all internal complexity.
- **Robustness First**: All calculations must handle polar boundaries, midnight rollovers, leap years, and Doze mode lifecycles.

## Development Workflow
1. Fork and clone the repository.
2. Open the project in Android Studio (Ladybug or newer).
3. Ensure all unit tests pass:
   ```bash
   ./gradlew test
   ```
4. Create a feature branch:
   ```bash
   git checkout -b feature/my-new-feature
   ```
5. Commit your changes with clear, descriptive commit messages.
6. Push to your branch and open a Pull Request.

## Reporting Issues
Please use the GitHub Issue Templates for bug reports and feature requests. Include details regarding device model, Android OS version, and active location coordinates/city if reporting calculation discrepancies.
