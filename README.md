# NativeImageConfigGenerator

AOT compiling Java applications via GraalVM's native image is becoming more and more popular, but there are extra steps needed to enable [Reflection Use](https://www.graalvm.org/22.0/reference-manual/native-image/Reflection/) and work with JNI. The automatic detection via GraalVM's agent works well, but it needs to exhaustively execute all code paths, which gets tedious in projects that change often.

This project is a simple annotation processor that automatically generates GraalVM native-image config files for reflection based dependency injection. For example, the annotated class below

```java
public class AnnotatedPojo {

    @Inject
    String someString;
    
    @FXML
    Button button;

    @FXML
    public void initialize(URL location, ResourceBundle resources) {
    }

}
```

produces a file in `META-INF/native-image/annotations/reflect-config-di.json` that contains the required configuration for using reflective DI:

```json
[{
    "name": "us.hebi.gui.AnnotatedPojo",
    "methods": [{
      "name": "initialize",
      "parameterTypes": ["java.net.URL", "java.util.ResourceBundle"]
    }],
    "fields": [{
        "name": "button"
    }, {
        "name": "someString"
    }]
}]
```

The annotation processor is automatically enabled when added as a dependency. This project was intended to be a quick demo to check how difficult this would be to implement, so for now there are no Maven releases, and it needs to be built manually for testing purposes

```xml
<dependency>
    <groupId>us.hebi.graalvm</groupId>
    <artifactId>annotation-processor</artifactId>
    <version>1.0-SNAPSHOT</version>
    <scope>compile</scope>
</dependency>
```
