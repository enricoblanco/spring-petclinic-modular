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

package org.springframework.samples.petclinic.visit;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase.Replace;
import org.springframework.transaction.annotation.Transactional;

/**
 * Integration test for {@link VisitRepository}.
 *
 * @author Wick Dynex
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = Replace.NONE)
class VisitRepositoryTests {

	@Autowired
	protected VisitRepository visits;

	@Test
	void shouldFindVisitsByPetId() {
		List<Visit> petVisits = this.visits.findByPetId(7);

		assertThat(petVisits)
			.hasSize(2)
			.element(0)
			.extracting(Visit::getDate)
			.isNotNull();
	}

	@Test
	@Transactional
	void shouldAddNewVisitForPet() {
		int initialSize = this.visits.findByPetId(7).size();

		Visit visit = new Visit();
		visit.setPetId(7);
		visit.setDate(LocalDate.now());
		visit.setDescription("New medical checkup");

		this.visits.save(visit);

		List<Visit> updatedVisits = this.visits.findByPetId(7);
		assertThat(updatedVisits)
			.hasSize(initialSize + 1)
			.last()
			.satisfies(v -> {
				assertThat(v.getId()).isNotNull();
				assertThat(v.getDescription()).isEqualTo("New medical checkup");
			});
	}

}
