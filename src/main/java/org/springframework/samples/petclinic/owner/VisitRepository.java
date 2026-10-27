/*
 * Copyright 2012-2025 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.springframework.samples.petclinic.owner;

import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

/**
 * Repository class for <code>Visit</code> domain objects. Visits have no owning pet
 * reference of their own, so they are reached through {@link Pet#getVisits()}.
 */
public interface VisitRepository extends Repository<Visit, Integer> {

	/**
	 * Retrieve the most recent visits of a pet, newest first.
	 * @param petId the pet whose visits are wanted
	 * @param pageable how many visits to return
	 * @return the most recent visits of the pet
	 */
	@Query("SELECT v FROM Pet p JOIN p.visits v WHERE p.id = :petId ORDER BY v.date DESC")
	@Transactional(readOnly = true)
	List<Visit> findRecentByPetId(@Param("petId") Integer petId, Pageable pageable);

}
