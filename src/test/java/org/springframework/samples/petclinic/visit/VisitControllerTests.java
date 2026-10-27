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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledInNativeImage;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.samples.petclinic.owner.OwnerExternalAPI;
import org.springframework.samples.petclinic.owner.PetDetails;
import org.springframework.samples.petclinic.vet.VetExternalAPI;
import org.springframework.samples.petclinic.vet.VetSummary;
import org.springframework.test.context.aot.DisabledInAotMode;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * Test class for {@link VisitController}
 *
 * @author Colin But
 * @author Wick Dynex
 */
@WebMvcTest(VisitController.class)
@DisabledInNativeImage
@DisabledInAotMode
class VisitControllerTests {

	private static final int TEST_OWNER_ID = 1;

	private static final int TEST_PET_ID = 1;

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private VisitRepository visits;

	@MockitoBean
	private OwnerExternalAPI ownerApi;

	@MockitoBean
	private VetExternalAPI vetApi;

	private PetDetails petDetails;

	@BeforeEach
	void init() {
		this.petDetails = new PetDetails(
			TEST_PET_ID,
			"Max",
			LocalDate.now(),
			"dog",
			"George",
			"Franklin"
		);
		given(this.ownerApi.findPetDetails(TEST_OWNER_ID, TEST_PET_ID)).willReturn(this.petDetails);
		given(this.vetApi.findAllVets()).willReturn(List.of(new VetSummary(1, "James Carter")));
	}

	@Test
	void initNewVisitForm() throws Exception {
		mockMvc.perform(get("/owners/{ownerId}/pets/{petId}/visits/new", TEST_OWNER_ID, TEST_PET_ID))
			.andExpect(status().isOk())
			.andExpect(view().name("pets/createOrUpdateVisitForm"));
	}

	@Test
	void initNewVisitFormLoadsPetAndPreviousVisitsThroughModuleApis() throws Exception {
		Visit previous = new Visit();
		previous.setPetId(TEST_PET_ID);
		previous.setDate(LocalDate.of(2013, 1, 1));
		previous.setDescription("rabies shot");
		given(this.visits.findByPetId(TEST_PET_ID)).willReturn(List.of(previous));

		MvcResult result = mockMvc
			.perform(get("/owners/{ownerId}/pets/{petId}/visits/new", TEST_OWNER_ID, TEST_PET_ID))
			.andExpect(status().isOk())
			.andReturn();

		Map<String, Object> model = result.getModelAndView().getModel();
		assertThat(model.get("pet")).isEqualTo(this.petDetails);
		assertThat(model.get("visits")).isEqualTo(List.of(previous));
		assertThat(model.get("minVisitDate")).isEqualTo(LocalDate.now().plusDays(1));
		VisitForm visit = (VisitForm) model.get("visit");
		assertThat(visit.isNew()).isTrue();
		assertThat(visit.getDate()).isEqualTo(LocalDate.now().plusDays(1));
		verify(this.ownerApi).findPetDetails(TEST_OWNER_ID, TEST_PET_ID);
	}

	@Test
	void initNewVisitFormFailsForUnknownPet() {
		given(this.ownerApi.findPetDetails(TEST_OWNER_ID, 99))
			.willThrow(new IllegalArgumentException("Pet with id 99 not found for owner with id 1."));

		assertThatThrownBy(
				() -> mockMvc.perform(get("/owners/{ownerId}/pets/{petId}/visits/new", TEST_OWNER_ID, 99)))
			.hasCauseInstanceOf(IllegalArgumentException.class);
		verify(this.visits, never()).findByPetId(99);
	}

	@Test
	void processNewVisitFormSavesVisitForPetAndIgnoresId() throws Exception {
		LocalDate date = LocalDate.now().plusDays(3);
		mockMvc
			.perform(post("/owners/{ownerId}/pets/{petId}/visits/new", TEST_OWNER_ID, TEST_PET_ID)
				.param("id", "99")
				.param("date", date.toString())
				.param("description", "Vaccination")
				.param("vetId", "1"))
			.andExpect(status().is3xxRedirection())
			.andExpect(flash().attribute("message", "Your visit has been booked"));

		ArgumentCaptor<Visit> saved = ArgumentCaptor.forClass(Visit.class);
		verify(this.visits).save(saved.capture());
		assertThat(saved.getValue().getId()).isNull();
		assertThat(saved.getValue().getPetId()).isEqualTo(TEST_PET_ID);
		assertThat(saved.getValue().getDate()).isEqualTo(date);
		assertThat(saved.getValue().getDescription()).isEqualTo("Vaccination");
		assertThat(saved.getValue().getVetId()).isEqualTo(1);
	}

	@Test
	void processNewVisitFormSuccess() throws Exception {
		mockMvc
			.perform(post("/owners/{ownerId}/pets/{petId}/visits/new", TEST_OWNER_ID, TEST_PET_ID)
				.param("name", "George")
				.param("date", LocalDate.now().plusDays(1).toString())
				.param("description", "Visit Description")
				.param("vetId", "1"))
			.andExpect(status().is3xxRedirection())
			.andExpect(view().name("redirect:/owners/{ownerId}"));

		verify(this.visits).save(any(Visit.class));
	}

	@Test
	void processNewVisitFormHasErrors() throws Exception {
		mockMvc
			.perform(post("/owners/{ownerId}/pets/{petId}/visits/new", TEST_OWNER_ID, TEST_PET_ID).param("name",
				"George"))
			.andExpect(model().attributeHasErrors("visit"))
			.andExpect(status().isOk())
			.andExpect(view().name("pets/createOrUpdateVisitForm"));

		verify(this.visits, never()).save(any(Visit.class));
	}

	@Test
	void processNewVisitFormHasErrorsWhenVisitDateIsNotInFuture() throws Exception {
		mockMvc
			.perform(post("/owners/{ownerId}/pets/{petId}/visits/new", TEST_OWNER_ID, TEST_PET_ID)
				.param("name", "George")
				.param("date", LocalDate.now().toString())
				.param("description", "Visit Description"))
			.andExpect(model().attributeHasFieldErrors("visit", "date"))
			.andExpect(model().attributeHasFieldErrorCode("visit", "date", "typeMismatch.visitDate"))
			.andExpect(status().isOk())
			.andExpect(view().name("pets/createOrUpdateVisitForm"));

		verify(this.visits, never()).save(any(Visit.class));
	}

	@Test
	void processNewVisitFormHasErrorsWhenDescriptionIsTooLong() throws Exception {
		mockMvc
			.perform(post("/owners/{ownerId}/pets/{petId}/visits/new", TEST_OWNER_ID, TEST_PET_ID)
				.param("name", "George")
				.param("date", LocalDate.now().plusDays(1).toString())
				.param("description", "a".repeat(256)))
			.andExpect(model().attributeHasFieldErrors("visit", "description"))
			.andExpect(status().isOk())
			.andExpect(view().name("pets/createOrUpdateVisitForm"));

		verify(this.visits, never()).save(any(Visit.class));
	}

	@Test
	void processNewVisitFormHasErrorsWhenVetIsMissing() throws Exception {
		mockMvc
			.perform(post("/owners/{ownerId}/pets/{petId}/visits/new", TEST_OWNER_ID, TEST_PET_ID)
				.param("date", LocalDate.now().plusDays(1).toString())
				.param("description", "Visit Description"))
			.andExpect(model().attributeHasFieldErrors("visit", "vetId"))
			.andExpect(status().isOk())
			.andExpect(view().name("pets/createOrUpdateVisitForm"));

		verify(this.visits, never()).save(any(Visit.class));
	}

}
