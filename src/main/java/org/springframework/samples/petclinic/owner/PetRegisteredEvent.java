package org.springframework.samples.petclinic.owner;

/**
 * Evento público publicado pelo módulo Owner quando um novo Pet é cadastrado.
 */
public record PetRegisteredEvent(
	Integer petId,
	Integer ownerId
) {
}
