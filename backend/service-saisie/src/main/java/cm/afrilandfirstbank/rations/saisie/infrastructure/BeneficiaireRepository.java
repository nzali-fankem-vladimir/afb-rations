package cm.afrilandfirstbank.rations.saisie.infrastructure;

import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import cm.afrilandfirstbank.rations.saisie.domaine.Beneficiaire;

/**
 * Accès aux bénéficiaires (base {@code rations_saisie}).
 *
 * <p>Rappel : <b>aucun enrôlement</b>. Ce repository ne charge jamais une liste
 * préétablie ; il retrouve un bénéficiaire déjà saisi ou laisse le service
 * applicatif en créer un ({@code ResolutionBeneficiaireService}, étape 4).
 */
@Repository
public interface BeneficiaireRepository extends JpaRepository<Beneficiaire, Long> {

    /**
     * Retrouve un bénéficiaire déjà saisi par son <b>seul</b> numéro de compte
     * courant.
     *
     * <p>C'est la recherche de déduplication au fil de la saisie : elle évite de
     * recréer un bénéficiaire déjà connu, condition de fiabilité de RG-04
     * (Sprint 3.2). Décision de l'étape 4 du Sprint 3.1 : <b>le compte courant
     * seul fait l'identité</b>. Le nom et le prénom se tapent à la main à chaque
     * saisie, sans liste ; les inclure dans la clé ferait d'une faute de frappe
     * un doublon de bénéficiaire, exactement le risque que RG-04 cherche à
     * écarter. Voir
     * {@code docs/decisions/2026-08-28-resolution-beneficiaire-et-incoherence-nom.md}.
     *
     * <p>{@code Optional} et non {@code List} : le compte courant est la clé
     * d'identité retenue. Aucune contrainte d'unicité ne l'impose encore en base
     * (piste de durcissement notée dans la décision) ; la garantie vient du fait
     * que la résolution passe toujours par cette recherche avant de créer.
     */
    Optional<Beneficiaire> findByNumCompteCourant(String numCompteCourant);

    /**
     * Recherche paginée par fragment de nom, insensible à la casse. Servira aux
     * écrans de suivi et de reporting des sous-sprints suivants ; aucune liste
     * complète de bénéficiaires n'est jamais renvoyée sans pagination.
     */
    Page<Beneficiaire> findByNomContainingIgnoreCase(String fragmentNom, Pageable pageable);

}
