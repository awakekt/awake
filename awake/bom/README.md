# awake:bom

`com.awakekt.awake:bom` is the Core version-alignment platform. Each Core release publishes it, in
the same Central deployment as the Core modules, with a constraint on every published Core coordinate
(root and per-target) at the release version. The Vulkan family and the WebGPU release pin are not
listed: they have their own versions.

Every Core module depends on it at its own version, as does the Vulkan backend at the Core version it
pins. A Gradle build that holds two Core versions resolves the platform to the newer one and raises
every Core module to match, so nothing needs declaring. To state the version yourself:

```kotlin
dependencies {
    implementation(platform("com.awakekt.awake:bom:<version>"))
}
```

Maven builds do not read Gradle metadata; import the BOM instead:

```xml
<dependencyManagement>
  <dependencies>
    <dependency>
      <groupId>com.awakekt.awake</groupId>
      <artifactId>bom</artifactId>
      <version>VERSION</version>
      <type>pom</type>
      <scope>import</scope>
    </dependency>
  </dependencies>
</dependencyManagement>
```

The module applies no Kotlin plugin and has no code. The root `build.gradle.kts` adds the edge to each
published module and fills in the constraints from the publications it finds, so the list is derived,
not kept by hand. See [the release process](../../docs/release-process.md#core-alignment-bom).
