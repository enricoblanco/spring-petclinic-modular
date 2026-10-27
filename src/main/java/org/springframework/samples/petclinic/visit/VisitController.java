package org.springframework.samples.petclinic.visit;

import java.time.LocalDate;
import java.util.Map;

import org.springframework.samples.petclinic.owner.OwnerExternalAPI;
import org.springframework.samples.petclinic.owner.PetDetails;
import org.springframework.stereotype.Controller;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.InitBinder;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import jakarta.validation.Valid;

@Controller
class VisitController {

	private final VisitRepository visits;

	private final OwnerExternalAPI ownerApi;

	public VisitController(VisitRepository visits, OwnerExternalAPI ownerApi) {
		this.visits = visits;
		this.ownerApi = ownerApi;
	}

	@InitBinder
	public void setAllowedFields(WebDataBinder dataBinder) {
		dataBinder.setDisallowedFields("id", "*.id");
	}

	@ModelAttribute("visit")
	public VisitForm loadPetWithVisit(@PathVariable("ownerId") int ownerId, @PathVariable("petId") int petId,
								  Map<String, Object> model) {
		PetDetails pet = this.ownerApi.findPetDetails(ownerId, petId);
		model.put("pet", pet);
		model.put("visits", this.visits.findByPetId(petId));
		return new VisitForm();
	}

	@ModelAttribute("minVisitDate")
	public LocalDate minVisitDate() {
		return LocalDate.now().plusDays(1);
	}

	@GetMapping("/owners/{ownerId}/pets/{petId}/visits/new")
	public String initNewVisitForm() {
		return "pets/createOrUpdateVisitForm";
	}

	@PostMapping("/owners/{ownerId}/pets/{petId}/visits/new")
	public String processNewVisitForm(@PathVariable int petId, @Valid @ModelAttribute("visit") VisitForm visit,
									  BindingResult result, RedirectAttributes redirectAttributes) {
		if (visit.getDate() != null && !visit.getDate().isAfter(LocalDate.now())) {
			result.rejectValue("date", "typeMismatch.visitDate");
		}

		if (result.hasErrors()) {
			return "pets/createOrUpdateVisitForm";
		}

		this.visits.save(visit.toVisit(petId));
		redirectAttributes.addFlashAttribute("message", "Your visit has been booked");
		return "redirect:/owners/{ownerId}";
	}

}
