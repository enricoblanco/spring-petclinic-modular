package org.springframework.samples.petclinic.visit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.time.LocalDate;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.samples.petclinic.owner.PetRegisteredEvent;

/**
 * Unit test for {@link PetRegistrationListener}.
 */
class PetRegistrationListenerTests {

	private final VisitRepository visits = mock(VisitRepository.class);

	private final PetRegistrationListener listener = new PetRegistrationListener(visits);

	@Test
	void shouldCreateInitialVisitForRegisteredPet() {
		listener.on(new PetRegisteredEvent(42, 1));

		ArgumentCaptor<Visit> captor = ArgumentCaptor.forClass(Visit.class);
		verify(visits).save(captor.capture());
		Visit visit = captor.getValue();
		assertThat(visit.getPetId()).isEqualTo(42);
		assertThat(visit.getDescription()).isEqualTo("Initial checkup");
		assertThat(visit.getDate()).isEqualTo(LocalDate.now().plusDays(1));
	}

}
