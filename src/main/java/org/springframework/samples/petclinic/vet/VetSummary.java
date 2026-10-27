package org.springframework.samples.petclinic.vet;

/**
 * DTO público exposto pelo módulo Vet com o id e o nome de exibição de um veterinário.
 */
public record VetSummary(Integer id, String name) {
}
