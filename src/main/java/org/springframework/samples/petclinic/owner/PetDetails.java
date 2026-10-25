package org.springframework.samples.petclinic.owner;

import java.time.LocalDate;

/**
 * DTO público exposto pelo módulo Owner para consulta de dados básicos do Pet por outros módulos.
 */
public record PetDetails(
	Integer id,
	String name,
	LocalDate birthDate,
	String type,
	String ownerFirstName,
	String ownerLastName
) {
}
