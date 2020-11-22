package grails.plugins.jsonapis

import grails.converters.JSON
import grails.core.GrailsApplication
import groovy.transform.CompileStatic
import groovy.util.logging.Log
import org.grails.datastore.mapping.model.PersistentEntity
import org.grails.web.converters.configuration.DefaultConverterConfiguration

/**
 * Keeps track of all the JSON APIs registered by the plugin. Constains methods
 * that are called on live reload events to update the APIs.
 */
@Log
@CompileStatic
class JsonApiRegistry {
	final Map<String, List<AnnotationMarshaller<JSON>>> marshallersByApi
	
	JsonApiRegistry() {
		marshallersByApi = [:].withDefault { new ArrayList<AnnotationMarshaller<JSON>>() }
	}
	
	/**
	 * Updates the state of all registered marshallers, adding new ones or
	 * deleting existing (in case of a live reload).
	 */
	void updateMarshallers(GrailsApplication application) {
		Collection<PersistentEntity> domainClasses = application.getMappingContext().getPersistentEntities()
		def allApiNames = getAllApiNames(domainClasses)
		
		def newApis = allApiNames - marshallersByApi.keySet()
		newApis.each { namespace ->
			JSON.createNamedConfig(namespace) { DefaultConverterConfiguration<JSON> cfg ->
				for (def domainClass : domainClasses) {
					def marshaller = new AnnotationMarshaller<JSON>(domainClass, namespace)
					cfg.registerObjectMarshaller(marshaller)
					marshallersByApi[namespace] << marshaller
				}
			}
		}
		
		def deletedApis = marshallersByApi.keySet() - allApiNames
		deletedApis.each { String apiName ->
			List deletedMarshallers = marshallersByApi[apiName]
			deletedMarshallers.each { it.deleted = true }
		}
		
		def remainingApis = allApiNames - deletedApis - newApis
		remainingApis.each { String apiName ->
			marshallersByApi[apiName]*.deleted = false
			marshallersByApi[apiName]*.initPropertiesToSerialize()
		}
	}
	
	/**
	 * Finds all API names defined in all domain classes.
	 * @return A set of all found namespace names.
	 */
	Set<String> getAllApiNames(Collection<PersistentEntity> domainClasses) {
		Set namespaces = []
		domainClasses.each { PersistentEntity domainClass ->
			domainClass.persistentPropertyNames.each { propertyName ->
				def declaredNamespaces = getPropertyAnnotationValue( domainClass.javaClass, propertyName )?.value()
				declaredNamespaces?.each { apiNamespace -> namespaces << apiNamespace }
			}
		}
		return namespaces
	}
	
	/**
	 * Finds the method or field corresponding to a Groovy property name and its JsonApi annotation if it exists.
	 */
	static JsonApi getPropertyAnnotationValue(Class clazz, String propertyName) {
		while (clazz) {
			def fieldOrMethod = clazz.declaredFields.find { it.name == propertyName } ?: clazz.declaredMethods.find { it.name == 'get' + propertyName.capitalize() }
			if (fieldOrMethod) return fieldOrMethod?.getAnnotation(JsonApi)
			else clazz = clazz.superclass
		}
		return null
	}

	/**
	 * Registers a single marshaller on a collection of domain classes - useful in unit tests!
	 * @param marshallerName Name of the marshaller to register.
	 * @param domainClass Classes on which we want to register the marshaller.
	 */
	static void registerMarshaller(String marshallerName, PersistentEntity... domainClasses) {
		JSON.createNamedConfig(marshallerName) { DefaultConverterConfiguration<JSON> cfg ->
			for (PersistentEntity domainClass : domainClasses) {
				def marshaller = new AnnotationMarshaller<JSON>(domainClass, marshallerName)
				cfg.registerObjectMarshaller(marshaller)
			}
		}

	}


}