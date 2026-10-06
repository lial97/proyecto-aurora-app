# iosApp

El proyecto Xcode se genera en una **Mac** (iOS solo se compila en macOS).

1. Instala Xcode y abre este repositorio en Android Studio o IntelliJ con el plugin *Kotlin Multiplatform*.
2. Crea aquí un proyecto Xcode "App" (SwiftUI) llamado `iosApp`.
3. En *Build Phases* añade antes de "Compile Sources" un script:
   ```sh
   cd "$SRCROOT/.."
   ./gradlew :composeApp:embedAndSignAppleFrameworkForXcode
   ```
4. En *Build Settings → Framework Search Paths* añade
   `$(SRCROOT)/../composeApp/build/xcode-frameworks/$(CONFIGURATION)/$(SDK_NAME)`
   y en *Other Linker Flags* `-framework ComposeApp`.
5. Muestra la interfaz compartida:
   ```swift
   import SwiftUI
   import ComposeApp

   struct ComposeView: UIViewControllerRepresentable {
       func makeUIViewController(context: Context) -> UIViewController { MainViewControllerKt.MainViewController() }
       func updateUIViewController(_ vc: UIViewController, context: Context) {}
   }

   @main
   struct iOSApp: App {
       var body: some Scene { WindowGroup { ComposeView().ignoresSafeArea() } }
   }
   ```

Alternativa rápida: generar un proyecto nuevo en <https://kmp.jetbrains.com> y copiar su carpeta `iosApp`.
