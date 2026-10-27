package org.springframework.samples.petclinic.vet;

import java.util.List;

/**
 * Fachada pública (API do módulo) para outros módulos consultarem os veterinários.
 */
public interface VetExternalAPI {

	List<VetSummary> findAllVets();

}
