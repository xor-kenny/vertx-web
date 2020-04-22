package io.vertx.ext.web.openapi.impl;

import io.vertx.core.Handler;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.json.pointer.JsonPointer;
import io.vertx.ext.web.api.service.ServiceRequest;
import io.vertx.ext.web.openapi.OpenAPIHolder;
import io.vertx.ext.web.openapi.RouterFactoryException;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * @author Francesco Guardiani @slinkydeveloper
 */
public class OpenApi3Utils {

  public static boolean isSchemaArray(JsonObject schema) {
    return "array".equals(schema.getString("type")) || schema.containsKey("items");
  }

  public static boolean isSchemaObjectOrAllOfType(JsonObject schema) {
    return isSchemaObject(schema) || schema.containsKey("allOf");
  }

  public static boolean isSchemaObjectOrCombinators(JsonObject schema) {
    return isSchemaObject(schema) || schema.containsKey("allOf") || schema.containsKey("oneOf") || schema.containsKey("anyOf");
  }

  public static boolean isSchemaObject(JsonObject schema) {
    return "object".equals(schema.getString("type")) || schema.containsKey("properties");
  }

  public static boolean isRequiredObjectProperty(JsonObject schema, String parameterName) {
    return schema.getJsonArray("required", new JsonArray()).contains(parameterName);
  }

  public static String resolveStyle(JsonObject param) {
    if (param.containsKey("style")) return param.getString("style");
    else switch (param.getString("in")) {
      case "query":
      case "cookie":
        return "form";
      case "path":
      case "header":
        return "simple";
    }
    return null; //TODO error reporting here?
  }

  public static boolean resolveExplode(JsonObject param) {
    String style = resolveStyle(param);
    return param.getBoolean("explode", "form".equals(style));
  }

//
//  public static boolean resolveAllowEmptyValue(JsonObject parameter) {
//    if (parameter.getAllowEmptyValue() != null) {
//      // As OAS says: This is valid only for query parameters and allows sending a parameter with an empty value. Default value is false. If style is used, and if behavior is n/a (cannot be serialized), the value of allowEmptyValue SHALL be ignored
//      if (!"form".equals(resolveStyle(parameter)))
//        return false;
//      else
//        return parameter.getAllowEmptyValue();
//    } else return false;
//  }
//
//  // Thank you StackOverflow :) https://stackoverflow
//  // .com/questions/28332924/case-insensitive-matching-of-a-string-to-a-java-enum :)
//  public static <T extends Enum<?>> T searchEnum(Class<T> enumeration, String search) {
//    for (T each : enumeration.getEnumConstants()) {
//      if (each.name().compareToIgnoreCase(search) == 0) {
//        return each;
//      }
//    }
//    return null;
//  }

  public static String resolveContentTypeRegex(String listContentTypes) {
    // Check if it's list
    if (listContentTypes.contains(",")) {
      StringBuilder stringBuilder = new StringBuilder();
      String[] contentTypes = listContentTypes.split(",");
      for (String contentType : contentTypes)
        stringBuilder.append(Pattern.quote(contentType.trim()) + "|");
      stringBuilder.deleteCharAt(stringBuilder.length() - 1);
      return stringBuilder.toString();
    } else if (listContentTypes.trim().endsWith("/*")) {
      StringBuilder stringBuilder = new StringBuilder();
      stringBuilder.append(listContentTypes.trim(), 0, listContentTypes.indexOf("/*"));
      stringBuilder.append("\\/.*");
      return stringBuilder.toString();
    } return Pattern.quote(listContentTypes);
  }
//
//  public static List<JsonObject> mergeParameters(List<JsonObject> operationParameters, List<JsonObject> parentParameters) {
//    List<JsonObject> result = new ArrayList<>(operationParameters);
//    List<JsonObject> actualParams = new ArrayList<>(operationParameters);
//    for (JsonObject parentParam : parentParameters) {
//      for (JsonObject actualParam : actualParams) {
//        if (!parentParam.getString("in").equalsIgnoreCase(actualParam.getString("in")) && parentParam.getString("name").equals(actualParam.getString("name")))
//          result.add(parentParam);
//      }
//    }
//    return result;
//  }

  /* This function resolve all properties inside an allOf array of schemas */
  protected static Map<String, JsonObject> resolveAllOfArrays(List<JsonObject> allOfSchemas) {
    Map<String, JsonObject> properties = new HashMap<>();
    for (JsonObject schema : allOfSchemas) {
      if ("object".equals(schema.getString("type")))
        throw RouterFactoryException.createUnsupportedSpecFeature("allOf allows only inner object types in parameters. Schema: " + schema.encode());
      schema.forEach(e -> properties.put(e.getKey(), (JsonObject) e.getValue()));
    }
    return properties;
  }

  /* This function check if schema is an allOf array or an object and returns a map of properties */
  protected static Map<String, JsonObject> solveObjectParameters(JsonObject schema) {
    if (schema.containsKey("allOf")) {
      return resolveAllOfArrays(schema.getJsonArray("allOf").stream().map(s -> (JsonObject)s).collect(Collectors.toList()));
    } else {
      return schema
        .getJsonObject("properties", new JsonObject())
        .stream()
        .collect(Collectors.toMap(Map.Entry::getKey, e -> (JsonObject) e.getValue()));
    }
  }
//
//  private final static Pattern COMPONENTS_REFS_MATCHER = Pattern.compile("^\\#\\/components\\/schemas\\/(.+)$");
//  private final static String COMPONENTS_REFS_SUBSTITUTION = "\\#\\/definitions\\/$1";
//
//  public static JsonNode generateSanitizedJsonSchemaNode(Schema s, OpenAPI oas) {
//    ObjectNode node = ObjectMapperFactory.createJson().convertValue(s, ObjectNode.class);
//    walkAndSolve(node, node, oas);
//    return node;
//  }
//
//  private static void walkAndSolve(ObjectNode n, ObjectNode root, OpenAPI oas) {
//    if (n.has("$ref")) {
//      replaceRef(n, root, oas);
//    } else if (n.has("allOf")) {
//      for (JsonNode jsonNode : n.get("allOf")) {
//        // We assert that parser validated allOf as array of objects
//        walkAndSolve((ObjectNode) jsonNode, root, oas);
//      }
//    } else if (n.has("anyOf")) {
//      for (JsonNode jsonNode : n.get("anyOf")) {
//        walkAndSolve((ObjectNode) jsonNode, root, oas);
//      }
//    } else if (n.has("oneOf")) {
//      for (JsonNode jsonNode : n.get("oneOf")) {
//        walkAndSolve((ObjectNode) jsonNode, root, oas);
//      }
//    } else if (n.has("properties")) {
//      ObjectNode properties = (ObjectNode) n.get("properties");
//      Iterator<String> it = properties.fieldNames();
//      while (it.hasNext()) {
//        walkAndSolve((ObjectNode) properties.get(it.next()), root, oas);
//      }
//    } else if (n.has("items")) {
//      walkAndSolve((ObjectNode) n.get("items"), root, oas);
//    } else if (n.has("additionalProperties")) {
//      JsonNode jsonNode = n.get("additionalProperties");
//      if (jsonNode.getNodeType().equals(JsonNodeType.OBJECT)) {
//        walkAndSolve((ObjectNode) n.get("additionalProperties"), root, oas);
//      }
//    }
//  }
//
//  private static void replaceRef(ObjectNode n, ObjectNode root, OpenAPI oas) {
//    /**
//     * If a ref is found, the structure of the schema is circular. The oas parser don't solve circular refs.
//     * So I bundle the schema:
//     * 1. I update the ref field with a #/definitions/schema_name uri
//     * 2. If #/definitions/schema_name is empty, I solve it
//     */
//    String oldRef = n.get("$ref").asText();
//    Matcher m = COMPONENTS_REFS_MATCHER.matcher(oldRef);
//    if (m.lookingAt()) {
//      String schemaName = m.group(1);
//      String newRef = m.replaceAll(COMPONENTS_REFS_SUBSTITUTION);
//      n.remove("$ref");
//      n.put("$ref", newRef);
//      if (!root.has("definitions") || !root.get("definitions").has(schemaName)) {
//        Schema s = oas.getComponents().getSchemas().get(schemaName);
//        ObjectNode schema = ObjectMapperFactory.createJson().convertValue(s, ObjectNode.class);
//        // We need to search inside for other refs
//        if (!root.has("definitions")) {
//          ObjectNode definitions = root.putObject("definitions");
//          definitions.set(schemaName, schema);
//        } else {
//          ((ObjectNode)root.get("definitions")).set(schemaName, schema);
//        }
//        walkAndSolve(schema, root, oas);
//      }
//    } else throw new RuntimeException("Wrong ref! " + oldRef);
//  }
//
//  public static List<MediaType> extractTypesFromMediaTypesMap(Map<String, MediaType> types, Predicate<String> matchingFunction) {
//    return types
//      .entrySet().stream()
//      .filter(e -> matchingFunction.test(e.getKey()))
//      .map(Map.Entry::getValue).collect(Collectors.toList());
//  }

  public static boolean serviceProxyMethodIsCompatibleHandler(Method method) {
    java.lang.reflect.Parameter[] parameters = method.getParameters();
    if (parameters.length < 2) return false;
    if (!parameters[parameters.length - 1].getType().equals(Handler.class)) return false;
    if (!parameters[parameters.length - 2].getType().equals(ServiceRequest.class)) return false;
    return true;
  }

  public static JsonObject sanitizeDeliveryOptionsExtension(JsonObject jsonObject) {
    JsonObject newObj = new JsonObject();
    if (jsonObject.containsKey("timeout")) newObj.put("timeout", jsonObject.getValue("timeout"));
    if (jsonObject.containsKey("headers")) newObj.put("headers", jsonObject.getValue("headers"));
    return newObj;
  }

  public static String sanitizeOperationId(String operationId) {
    StringBuffer result = new StringBuffer();
    for (int i = 0; i < operationId.length(); i++) {
      char c = operationId.charAt(i);
      if (c == '-' || c == ' ' || c == '_') {
        try {
          while (c == '-' || c == ' ' || c == '_') {
            i++;
            c = operationId.charAt(i);
          }
          result.append(Character.toUpperCase(operationId.charAt(i)));
        } catch (StringIndexOutOfBoundsException e) {}
      } else {
        result.append(c);
      }
    }
    return result.toString();
  }

  public static Object getAndMergeServiceExtension(String extensionKey, String addressKey, String methodKey, JsonObject pathModel, JsonObject operationModel) {
    Object pathExtension = pathModel.getValue(extensionKey);
    Object operationExtension = operationModel.getValue(extensionKey);

    // Cases:
    // 1. both strings or path extension null: operation extension overrides all
    // 2. path extension map and operation extension string: path extension interpreted as delivery options and operation extension as address
    // 3. path extension string and operation extension map: path extension interpreted as address
    // 4. both maps: extension map overrides path map elements
    // 5. operation extension null: path extension overrides all

    if ((operationExtension instanceof String && pathExtension instanceof String) || pathExtension == null) return operationExtension;
    if (operationExtension instanceof String && pathExtension instanceof JsonObject) {
      JsonObject result = new JsonObject();
      result.put(addressKey, operationExtension);
      JsonObject pathExtensionMap = (JsonObject) pathExtension;
      if (pathExtensionMap.containsKey(methodKey)) throw RouterFactoryException.createWrongExtension("Extension " + extensionKey + " in path declaration must not contain " + methodKey);
      result.mergeIn(pathExtensionMap);
      return result;
    }
    if (operationExtension instanceof JsonObject && pathExtension instanceof String) {
      JsonObject result = ((JsonObject) operationExtension).copy();
      if (!result.containsKey(addressKey))
        result.put(addressKey, pathExtension);
      return result;
    }
    if (operationExtension instanceof JsonObject && pathExtension instanceof JsonObject) {
      return ((JsonObject)pathExtension).copy().mergeIn((JsonObject) operationExtension);
    }
    if (operationExtension == null) return pathExtension;
    return null;
  }

  // /definitions/hello/properties/a - /definitions/hello = /properties/a
  public static JsonPointer pointerDifference(JsonPointer pointer1, JsonPointer pointer2) {
    String firstPointer = pointer1.toString();
    String secondPointer = pointer2.toString();

    return JsonPointer.from(
      firstPointer.substring(secondPointer.length())
    );
  }

  public static JsonObject generateFakeSchema(JsonObject schema, OpenAPIHolder holder) {
    JsonObject fakeSchema = holder.solveIfNeeded(schema).copy();
    String combinatorKeyword = fakeSchema.containsKey("allOf") ? "allOf" : fakeSchema.containsKey("anyOf") ? "anyOf" : fakeSchema.containsKey("oneOf") ? "oneOf" : null;
    if (combinatorKeyword != null) {
      JsonArray schemasArray = fakeSchema.getJsonArray(combinatorKeyword);
      JsonArray processedSchemas = new JsonArray();
      for (int i = 0; i < schemasArray.size(); i++) {
        JsonObject innerSchema = holder.solveIfNeeded(schemasArray.getJsonObject(i));
        processedSchemas.add(innerSchema.copy());
        schemasArray.getJsonObject(i).mergeIn(innerSchema);
        if ("object".equals(innerSchema.getString("type")) || innerSchema.containsKey("properties"))
          fakeSchema = fakeSchema.mergeIn(innerSchema, true);
      }
      fakeSchema.remove(combinatorKeyword);
      fakeSchema.put("x-" + combinatorKeyword, processedSchemas);
    }
    if (fakeSchema.containsKey("properties")) {
      JsonObject propsObj = fakeSchema.getJsonObject("properties");
      for (String key : propsObj.fieldNames()) {
        propsObj.put(key, holder.solveIfNeeded(propsObj.getJsonObject(key)));
      }
    }
    if (fakeSchema.containsKey("items")) {
      fakeSchema.put("items", holder.solveIfNeeded(fakeSchema.getJsonObject("items")));
    }

    return fakeSchema;

  }

  public static boolean isFakeSchemaAnyOfOrOneOf(JsonObject fakeSchema) {
    return fakeSchema.containsKey("x-anyOf") || fakeSchema.containsKey("x-oneOf");
  }

}
