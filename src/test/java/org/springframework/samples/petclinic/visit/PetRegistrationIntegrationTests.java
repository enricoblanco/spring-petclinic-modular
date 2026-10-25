package org.springframework.samples.petclinic.visit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

/**
 * End-to-end test: registering a pet publishes an event that creates its initial visit.
 */
@SpringBootTest
@AutoConfigureMockMvc
class PetRegistrationIntegrationTests {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private VisitRepository visits;

	@Autowired
	private JdbcTemplate jdbc;

	@Test
	void shouldCreateInitialVisitWhenPetIsRegistered() throws Exception {
		// not @Transactional: the listener only runs after the commit
		String name = "Pet-" + UUID.randomUUID().toString().substring(0, 8);

		mockMvc
			.perform(post("/owners/{ownerId}/pets/new", 1).param("name", name)
				.param("type", "cat")
				.param("birthDate", "2020-01-01"))
			.andExpect(status().is3xxRedirection());

		Integer petId = jdbc.queryForObject("SELECT id FROM pets WHERE name = ?", Integer.class, name);
		List<Visit> petVisits = visits.findByPetId(petId);
		assertThat(petVisits).singleElement()
			.extracting(Visit::getDescription)
			.isEqualTo(PetRegistrationListener.INITIAL_VISIT_DESCRIPTION);

		// the owner page shows it through OwnerPageVisitsAdvice
		mockMvc.perform(get("/owners/{ownerId}", 1))
			.andExpect(status().isOk())
			.andExpect(content().string(org.hamcrest.Matchers.containsString(name)))
			.andExpect(content().string(org.hamcrest.Matchers.containsString(PetRegistrationListener.INITIAL_VISIT_DESCRIPTION)));
	}

}
