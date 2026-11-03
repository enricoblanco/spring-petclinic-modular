package org.springframework.samples.petclinic.amostra;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Anotação de pacote, usada apenas para que o compilador gere package-info.class. */
@Retention(RetentionPolicy.CLASS)
@Target(ElementType.PACKAGE)
@interface Marca {

}
