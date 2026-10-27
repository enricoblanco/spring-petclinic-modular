package org.springframework.samples.petclinic.owner;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase.Replace;
import org.springframework.data.domain.PageRequest;

/**
 * Integration test for {@link VisitRepository}.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = Replace.NONE)
class VisitRepositoryTests {

	@Autowired
	private OwnerRepository owners;

	@Autowired
	private VisitRepository visits;

	@Test
	void shouldFindOnlyTheMostRecentVisitsNewestFirst() {
		Owner owner = this.owners.findById(6).orElseThrow();
		Pet samantha = owner.getPet(7);
		for (int daysAgo = 1; daysAgo <= 6; daysAgo++) {
			Visit visit = new Visit();
			visit.setDate(LocalDate.now().minusDays(daysAgo));
			visit.setDescription("checkup " + daysAgo);
			samantha.addVisit(visit);
		}
		this.owners.saveAndFlush(owner);

		List<Visit> recent = this.visits.findRecentByPetId(7, PageRequest.of(0, 5));

		assertThat(recent).hasSize(5)
			.extracting(Visit::getDate)
			.containsExactly(LocalDate.now().minusDays(1), LocalDate.now().minusDays(2), LocalDate.now().minusDays(3),
					LocalDate.now().minusDays(4), LocalDate.now().minusDays(5));
	}

}
