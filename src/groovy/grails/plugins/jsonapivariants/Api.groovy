package grails.plugins.jsonapivariants

import java.lang.annotation.Retention
import java.lang.annotation.RetentionPolicy

@Retention( RetentionPolicy.RUNTIME )
public @interface Api {
	String[] value() default []
}
