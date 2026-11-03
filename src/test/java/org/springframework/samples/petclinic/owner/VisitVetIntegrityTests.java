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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.SQLIntegrityConstraintViolationException;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase.Replace;

/**
 * Database integrity of the visit to vet reference, written in SQL so that it checks the
 * schema itself and reads the same in every version of the application.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = Replace.NONE)
class VisitVetIntegrityTests {

	@Autowired
	private EntityManager em;

	@Test
	void shouldRejectVisitReferencingMissingVet() {
		assertThatThrownBy(() -> insertVisit("999"))
			.hasRootCauseInstanceOf(SQLIntegrityConstraintViolationException.class);
	}

	@Test
	void shouldAcceptVisitReferencingExistingVetOrNoVet() {
		assertThat(insertVisit("1")).isEqualTo(1);
		assertThat(insertVisit("NULL")).isEqualTo(1);
	}

	private int insertVisit(String vetId) {
		return this.em
			.createNativeQuery("INSERT INTO visits (pet_id, visit_date, description, vet_id) "
					+ "VALUES (7, CURRENT_DATE, 'integrity check', " + vetId + ")")
			.executeUpdate();
	}

}
