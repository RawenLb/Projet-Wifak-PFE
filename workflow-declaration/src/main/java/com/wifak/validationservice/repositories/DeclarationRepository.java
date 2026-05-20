package com.wifak.validationservice.repositories;

import com.wifak.validationservice.entities.Declaration;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface DeclarationRepository extends JpaRepository<Declaration, Long> {
    List<Declaration> findByGenerePar(String username);
    List<Declaration> findByStatut(Declaration.DeclarationStatut statut);
    List<Declaration> findByDeclarationTypeId(Long typeId);
    List<Declaration> findByPeriode(String periode);
    List<Declaration> findByDeclarationTypeIdAndPeriode(Long typeId, String periode);
    long countByStatut(Declaration.DeclarationStatut statut);
}
