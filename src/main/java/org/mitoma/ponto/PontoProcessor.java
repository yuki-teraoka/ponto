package org.mitoma.ponto;

import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.InvalidPropertiesFormatException;
import java.util.List;
import java.util.Properties;
import java.util.Set;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.Filer;
import javax.annotation.processing.Messager;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.annotation.processing.SupportedSourceVersion;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import javax.tools.Diagnostic;
import javax.tools.Diagnostic.Kind;
import javax.tools.JavaFileObject;
import javax.tools.StandardLocation;

@SupportedSourceVersion(SourceVersion.RELEASE_7)
@SupportedAnnotationTypes("org.mitoma.ponto.ConstantResource")
public class PontoProcessor extends AbstractProcessor {

  @Override
  public boolean process(Set<? extends TypeElement> annotations,
      RoundEnvironment roundEnv) {
    for (Element elem : roundEnv
        .getElementsAnnotatedWith(ConstantResource.class)) {
      ConstantResource annotation = elem.getAnnotation(ConstantResource.class);
      Messager messager = processingEnv.getMessager();
      try {
        generateSource(annotation);
      } catch (IOException | IllegalArgumentException e) {
        messager.printMessage(Diagnostic.Kind.ERROR, e.toString());
        return false;
      }
    }
    return true;
  }

  private void generateSource(ConstantResource annotation) throws IOException {
    String[] propFiles = annotation.value();
    String packageName = annotation.packageName();
    String className = annotation.className();
    Filer filer = processingEnv.getFiler();
    Properties properties = loadProperties(propFiles);
    List<String> errors = validation(properties);
    if (!errors.isEmpty()) {
      Messager messager = processingEnv.getMessager();
      for (String err : errors) {
        messager.printMessage(Kind.ERROR, err);
      }
      throw new IllegalArgumentException("invalid propertie file.");
    }

    JavaFileObject source = filer.createSourceFile(String.format("%s.%s",
        packageName, className));
    PrintWriter pw = new PrintWriter(source.openOutputStream(), true);
    if (!packageName.isEmpty()) {
      pw.println(String.format("package %s;", packageName));
    }
    pw.println(String.format("public class %s {", className));

    annotation.keyStyle().writeMethods(pw, properties);

    pw.println("  private static java.util.Properties envProperties = new java.util.Properties();");
    pw.println("  private static java.util.Properties properties = new java.util.Properties();");
    pw.println("  static {");
    pw.println("    try{");
    for (String propFile : propFiles) {
      pw.println(String.format("      loadProperties(\"%s\");", propFile));
    }
    pw.println("    } catch (java.io.IOException e) {");
    pw.println("        throw new RuntimeException(\"Ponto initialize error.\", e);");
    pw.println("    }");
    pw.println("  }");
    pw.println("  private static String getProperties(String key){");
    pw.println("    if (envProperties.containsKey(key)) {");
    pw.println("      return envProperties.getProperty(key);");
    pw.println("    }");
    pw.println("    return properties.getProperty(key);");
    pw.println("  }");
    String envKey = annotation.envKey();
    String envDefault = annotation.envDefault();
    pw.println("  private static void loadProperties(String propFile) throws java.io.IOException {");
    pw.println("    ClassLoader loader = ClassLoader.getSystemClassLoader();");
    pw.println("    boolean isXml = propFile.endsWith(\".xml\");");
    pw.println("    String env = System.getenv(\"" + envKey + "\");");
    pw.println("    if (env == null) {");
    pw.println("      env = \"" + envDefault + "\";");
    pw.println("    }");
    pw.println("    int lastIndex = propFile.lastIndexOf(\".\");");
    pw.println("    String pre = propFile.substring(0, lastIndex);");
    pw.println("    String post = propFile.substring(lastIndex, propFile.length());");
    pw.println("    String envPropFile = String.format(\"%s_%s%s\", pre, env, post);");
    pw.println("    if (isXml) {");
    pw.println("      if (loader.getResourceAsStream(envPropFile) != null) {");
    pw.println("        envProperties.loadFromXML(loader.getResourceAsStream(envPropFile));");
    pw.println("      }");
    pw.println("      properties.loadFromXML(loader.getResourceAsStream(propFile));");
    pw.println("    } else {");
    pw.println("      if (loader.getResourceAsStream(envPropFile) != null) {");
    pw.println("        envProperties.load(loader.getResourceAsStream(envPropFile));");
    pw.println("      }");
    pw.println("      properties.load(loader.getResourceAsStream(propFile));");
    pw.println("    }");
    pw.println("  }");
    pw.println("}");
    pw.flush();
    pw.close();
  }

  private Properties loadProperties(String[] propFiles) throws IOException,
      InvalidPropertiesFormatException {
    Properties properties = new Properties();
    Filer filer = processingEnv.getFiler();
    for (String propFile : propFiles) {
      InputStream stream = filer.getResource(StandardLocation.CLASS_PATH, "",
          propFile).openInputStream();
      if (propFile.endsWith(".xml")) {
        properties.loadFromXML(stream);
      } else {
        properties.load(stream);
      }
    }
    return properties;
  }

  private List<String> validation(Properties properties) {
    List<String> errors = new ArrayList<>();
    for (Object key : properties.keySet()) {
      String keyStr = (String) key;
      MethodType methodType = MethodType.findMethodType(keyStr);
      String value = properties.getProperty(keyStr);
      if (!methodType.isValid(value)) {
        errors.add(String.format(
            "invalid key. type[%s], format[%s], value[%s]", methodType, keyStr,
            value));
      }
    }
    return errors;
  }
}
