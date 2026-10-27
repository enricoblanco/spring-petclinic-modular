package org.springframework.samples.petclinic.visit;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
interface VisitRepository extends JpaRepository<Visit, Integer> {

	@Transactional(readOnly = true)
	List<Visit> findByPetId(Integer petId);

	@Transactional(readOnly = true)
	List<Visit> findTop5ByPetIdOrderByDateDesc(Integer petId);

}
