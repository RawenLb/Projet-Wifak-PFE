package com.example.bctbackend.repositories;

import com.example.bctbackend.entities.Declaration;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface DeclarationRepository extends JpaRepository<Declaration, Long> {

    /**
     * ✅ Trouver toutes les déclarations d'un utilisateur
     */
    List<Declaration> findByGenerePar(String username);

    /**
     * ✅ Trouver les déclarations par statut
     */
    List<Declaration> findByStatut(Declaration.DeclarationStatut statut);

    /**
     * ✅ Trouver les déclarations par type
     */
    List<Declaration> findByDeclarationTypeId(Long typeId);

    /**
     * ✅ Trouver les déclarations par période
     */
    List<Declaration> findByPeriode(String periode);

    /**
     * ✅ Trouver les déclarations par type et période
     */
    List<Declaration> findByDeclarationTypeIdAndPeriode(Long typeId, String periode);

    /**
     * ✅ Compter les déclarations par statut
     */
    long countByStatut(Declaration.DeclarationStatut statut);
}