package org.springframework.samples.petclinic.visit;

import java.time.LocalDate;

import org.springframework.format.annotation.DateTimeFormat;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Form backing object for booking a visit, so request parameters are never bound straight onto the {@link Visit}
 * entity.
 */
class VisitForm {

	@DateTimeFormat(pattern = "yyyy-MM-dd")
	private LocalDate date = LocalDate.now().plusDays(1);

	@NotBlank
	@Size(max = 255)
	private String description;

	public LocalDate getDate() {
		return this.date;
	}

	public void setDate(LocalDate date) {
		this.date = date;
	}

	public String getDescription() {
		return this.description;
	}

	public void setDescription(String description) {
		this.description = description;
	}

	/**
	 * The form only ever books new visits; the template uses this for its heading.
	 */
	public boolean isNew() {
		return true;
	}

	Visit toVisit(int petId) {
		Visit visit = new Visit();
		visit.setPetId(petId);
		visit.setDate(this.date);
		visit.setDescription(this.description);
		return visit;
	}

}
