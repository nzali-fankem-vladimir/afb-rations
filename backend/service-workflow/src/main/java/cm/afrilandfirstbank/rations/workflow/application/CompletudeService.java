package cm.afrilandfirstbank.rations.workflow.application;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;

import cm.afrilandfirstbank.rations.workflow.domaine.CodeManqueEnum;
import cm.afrilandfirstbank.rations.workflow.domaine.ProcessusMensuel;

/**
 * Controle de completude d'un etat mensuel, avant sa soumission (US-07, CT-13).
 *
 * <h2>Ce que la soumission engage</h2>
 *
 * <p>C'est ce qui donne sa mesure a ce controle. Passe la soumission, le service
 * Saisie refuse toute ecriture, {@code montant_total} est grave sur le processus,
 * ce montant commande l'aiguillage RG-08, et a la cloture les lignes deviennent la
 * charge du topic {@code rations.etat.valide} — c'est-a-dire le paiement. Ce
 * controle est <b>le dernier point ou un etat peut encore etre corrige</b>.
 *
 * <h2>Les quatre controles retenus</h2>
 *
 * <p>Le cahier des charges dit que « le systeme verifie la completude des
 * informations » sans preciser lesquelles. La liste a ete arbitree au Sprint 4.2 ;
 * {@code docs/controles-completude.md} porte le detail, y compris ce qui a ete
 * ecarte et pourquoi.
 *
 * <ol>
 *   <li>{@link CodeManqueEnum#ETAT_VIDE} — aucune ligne a soumettre</li>
 *   <li>{@link CodeManqueEnum#LIGNE_HORS_PERIODE} — une ligne rattachee a une
 *       journee etrangere au mois du processus</li>
 *   <li>{@link CodeManqueEnum#LIGNE_SANS_MONTANT} — une ligne sans montant</li>
 *   <li>{@link CodeManqueEnum#BENEFICIAIRE_SANS_COMPTE} — un beneficiaire sans
 *       numero de compte courant</li>
 * </ol>
 *
 * <h2>Ce service ne calcule aucun montant</h2>
 *
 * <p>Il lit {@code montantApplique} ligne par ligne pour verifier sa
 * <i>presence</i>, jamais pour en faire une somme. Le total vient de
 * {@code montantTotalFcfa}, produit par le service Saisie et recopie tel quel
 * (RG-06 partagee, decision Sprint 3.4 : aucun second chemin de calcul, donc
 * aucune divergence possible entre le detail affiche et le total).
 *
 * <h2>Sans etat, sans base, sans reseau</h2>
 *
 * <p>Toutes les donnees necessaires sont deja dans les deux parametres. Le
 * service ne consulte ni la base, ni le service Saisie, ni le service Identite :
 * c'est l'appelant qui a obtenu l'etat consolide, et c'est lui qui portera le
 * refus. Cela le rend testable sans aucune infrastructure.
 */
@Service
public class CompletudeService {

    /** Format des journees dans les messages : celui que l'agent lit sur ses fiches. */
    private static final DateTimeFormatter JOUR = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    /**
     * Nombre d'exemples nommes dans un message avant troncature. Au-dela, le
     * message annonce le reste en nombre : une enumeration de deux cents
     * identifiants n'aide personne.
     */
    private static final int EXEMPLES_MAX = 5;

    /**
     * Verifie qu'un etat consolide peut etre soumis.
     *
     * @param processus le processus tel que Workflow le connait — c'est lui qui
     *        porte la periode de reference du controle de coherence
     * @param etat l'etat consolide obtenu du service Saisie
     * @return les manques constates, dans un ordre stable ; vide si l'etat est
     *         complet. <b>Jamais {@code null}, jamais une exception</b> : ce
     *         service constate, il ne refuse pas. Le refus appartient au service
     *         de soumission, qui seul sait ce qu'il faut faire d'un manque.
     */
    public ResultatCompletude verifier(ProcessusMensuel processus, EtatConsolide etat) {
        List<LigneSituee> lignes = lignesSituees(etat);

        // L'etat vide court-circuite les trois autres controles : ils n'auraient
        // aucune ligne a examiner et n'ajouteraient rien, tandis que le message
        // « aucune ligne » se suffit a lui-meme.
        Optional<ManqueCompletude> vide = controlerEtatNonVide(processus, etat, lignes);
        if (vide.isPresent()) {
            return new ResultatCompletude(List.of(vide.get()));
        }

        List<ManqueCompletude> manques = new ArrayList<>();
        controlerPeriode(processus, lignes).ifPresent(manques::add);
        controlerMontants(lignes).ifPresent(manques::add);
        controlerComptesCourants(lignes).ifPresent(manques::add);

        return new ResultatCompletude(manques);
    }

    // --- Controle 1 : l'etat n'est pas vide ------------------------------------

    /**
     * RG implicite du Sprint 3.4 (point C-03) : un etat sans aucune ligne ne se
     * soumet pas.
     *
     * <p><b>Deux facons d'etre vide, et la seconde compte autant.</b> Le compteur
     * {@code nombreLignes} annonce par le service Saisie peut valoir zero — cas
     * normal d'un etat qu'on vient d'ouvrir — ou etre absent de la reponse, donc
     * lu {@code null} par le <i>tolerant reader</i>. Mais le detail peut aussi ne
     * porter aucune ligne alors que le compteur en annonce. Ce desaccord ne
     * devrait jamais arriver ; s'il arrive, on refuse plutot que de soumettre un
     * etat dont on ne voit pas le contenu, et le message le dit franchement au
     * lieu de faire croire a un oubli de l'agent.
     */
    private Optional<ManqueCompletude> controlerEtatNonVide(ProcessusMensuel processus,
            EtatConsolide etat, List<LigneSituee> lignes) {

        Integer compteurAnnonce = etat.nombreLignes();
        boolean compteurNul = compteurAnnonce == null || compteurAnnonce == 0;

        if (!compteurNul && lignes.isEmpty()) {
            return Optional.of(new ManqueCompletude(CodeManqueEnum.ETAT_VIDE,
                    "Le service Saisie annonce " + compteurAnnonce + " ligne(s) mais n'en detaille "
                            + "aucune. L'etat n'est pas soumis sur une reponse incoherente : "
                            + "reaffichez le detail du mois avant de recommencer."));
        }

        if (compteurNul || lignes.isEmpty()) {
            return Optional.of(new ManqueCompletude(CodeManqueEnum.ETAT_VIDE,
                    "L'etat de " + periode(processus) + " pour l'unite " + processus.getCodeUnite()
                            + " ne contient aucune ligne de prestation. Saisissez au moins une "
                            + "prestation avant de soumettre."));
        }

        return Optional.empty();
    }

    // --- Controle 2 : coherence de periode -------------------------------------

    /**
     * Aucune ligne ne doit etre rattachee a une journee etrangere au mois du
     * processus.
     *
     * <p><b>Le controle porte sur les lignes, pas sur les journees.</b> Une journee
     * ouverte hors periode mais restee vide ne bloque pas : elle ne porte aucun
     * montant, et le contrat d'API n'offre aucun {@code DELETE /saisie/fiches/{id}}
     * — bloquer dessus enfermerait definitivement l'agent, qui ne peut supprimer
     * que des lignes. C'est aussi pourquoi le message renvoie vers la suppression
     * des lignes : un refus qui ne nomme pas le recours laisse l'agent sans issue
     * (doctrine Sprint 3.3).
     *
     * <p><b>Une journee sans date est traitee comme hors periode.</b> Elle ne peut
     * pas etre <i>prouvee</i> dans le mois, et le refus par defaut vaut aussi pour
     * les valeurs absentes (meme raisonnement que {@code estAutorisee} sur un
     * statut nul, Sprint 4.1). Le message distingue les deux cas pour ne pas
     * accuser l'agent d'une erreur qui n'est pas la sienne.
     */
    private Optional<ManqueCompletude> controlerPeriode(ProcessusMensuel processus,
            List<LigneSituee> lignes) {

        List<String> journeesFautives = new ArrayList<>();
        int lignesConcernees = 0;
        boolean dateAbsente = false;

        for (LigneSituee situee : lignes) {
            LocalDate dateJour = situee.dateJour();
            if (dansLaPeriode(dateJour, processus)) {
                continue;
            }
            lignesConcernees++;
            if (dateJour == null) {
                dateAbsente = true;
            }
            String libelle = libelleJournee(situee);
            if (!journeesFautives.contains(libelle)) {
                journeesFautives.add(libelle);
            }
        }

        if (lignesConcernees == 0) {
            return Optional.empty();
        }

        String detail = dateAbsente
                ? " Une journee sans date ne peut pas etre rattachee a un mois : signalez-la a "
                        + "l'administrateur du module."
                : " Supprimez ces lignes (suppression d'une ligne de prestation) avant de soumettre, "
                        + "ou reportez-les sur l'etat du mois auquel elles appartiennent.";

        return Optional.of(new ManqueCompletude(CodeManqueEnum.LIGNE_HORS_PERIODE,
                lignesConcernees + " ligne(s) sont rattachees a des journees hors de la periode "
                        + periode(processus) + " : " + enumerer(journeesFautives) + "." + detail));
    }

    // --- Controle 3 : montant present ------------------------------------------

    /**
     * Aucune ligne ne doit etre depourvue de montant applicable.
     *
     * <p>Un montant nul ou negatif est traite comme absent : les deux
     * produiraient une ligne a payer pour rien, et {@code 0} est presque toujours
     * le signe d'une valeur qui n'a pas ete resolue plutot qu'un tarif reel — le
     * contrat de creation d'une grille exige d'ailleurs un montant strictement
     * positif (Sprint 2.2).
     */
    private Optional<ManqueCompletude> controlerMontants(List<LigneSituee> lignes) {
        List<String> fautives = new ArrayList<>();
        for (LigneSituee situee : lignes) {
            Integer montant = situee.ligne().montantApplique();
            if (montant == null || montant <= 0) {
                fautives.add(referenceLigne(situee));
            }
        }
        if (fautives.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new ManqueCompletude(CodeManqueEnum.LIGNE_SANS_MONTANT,
                fautives.size() + " ligne(s) ne portent aucun montant applicable : "
                        + enumerer(fautives) + ". Le montant est repris de la grille active au "
                        + "moment de la saisie (RG-03) ; une ligne sans montant ne peut pas etre "
                        + "payee. Supprimez-la puis ressaisissez-la."));
    }

    // --- Controle 4 : compte courant du beneficiaire ---------------------------

    /**
     * Aucun beneficiaire ne doit etre depourvu de numero de compte courant.
     *
     * <p>C'est la donnee qui commande <b>qui est paye</b> : elle porte la ligne de
     * credit dans la charge du topic {@code rations.etat.valide} (contrat section
     * 7.1). Un beneficiaire entierement absent de la ligne est traite ici plutot
     * que dans un cinquieme controle : le manque est le meme du point de vue de
     * l'agent — cette ligne n'est pas payable — et le message le nomme
     * precisement.
     */
    private Optional<ManqueCompletude> controlerComptesCourants(List<LigneSituee> lignes) {
        List<String> fautifs = new ArrayList<>();
        for (LigneSituee situee : lignes) {
            EtatConsolide.Beneficiaire beneficiaire = situee.ligne().beneficiaire();
            if (beneficiaire == null) {
                fautifs.add("beneficiaire absent (" + referenceLigne(situee) + ")");
                continue;
            }
            String compte = beneficiaire.numCompteCourant();
            if (compte == null || compte.isBlank()) {
                fautifs.add(libelleBeneficiaire(beneficiaire) + " (" + referenceLigne(situee) + ")");
            }
        }
        if (fautifs.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new ManqueCompletude(CodeManqueEnum.BENEFICIAIRE_SANS_COMPTE,
                fautifs.size() + " ligne(s) concernent un beneficiaire sans numero de compte "
                        + "courant : " + enumerer(fautifs) + ". Le numero de compte est la donnee "
                        + "qui commande la mise en paiement : sans lui, la ligne est impayable."));
    }

    // --- Lecture de l'etat consolide -------------------------------------------

    /**
     * Aplatit l'etat consolide en lignes, chacune gardant la journee dont elle
     * releve.
     *
     * <p>Chaque controle a besoin de nommer la journee dans son message ; les
     * parcourir separement obligerait a refaire l'association trois fois. Les
     * collections nulles sont traitees comme vides — {@link EtatConsolide} est un
     * <i>tolerant reader</i>, une reponse tronquee ne doit pas produire une
     * {@code NullPointerException} au milieu d'un controle de securite.
     */
    private List<LigneSituee> lignesSituees(EtatConsolide etat) {
        List<LigneSituee> lignes = new ArrayList<>();
        if (etat == null || etat.journees() == null) {
            return lignes;
        }
        for (EtatConsolide.Journee journee : etat.journees()) {
            if (journee == null || journee.lignes() == null) {
                continue;
            }
            for (EtatConsolide.Ligne ligne : journee.lignes()) {
                if (ligne != null) {
                    lignes.add(new LigneSituee(journee, ligne));
                }
            }
        }
        return lignes;
    }

    /** Une ligne de prestation et la journee a laquelle elle est rattachee. */
    private record LigneSituee(EtatConsolide.Journee journee, EtatConsolide.Ligne ligne) {

        LocalDate dateJour() {
            return journee.dateJour();
        }
    }

    // --- Mise en forme ----------------------------------------------------------

    private boolean dansLaPeriode(LocalDate dateJour, ProcessusMensuel processus) {
        if (dateJour == null || processus.getMoisPaiement() == null
                || processus.getAnneePaiement() == null) {
            return false;
        }
        return dateJour.getMonthValue() == processus.getMoisPaiement()
                && dateJour.getYear() == processus.getAnneePaiement();
    }

    private String periode(ProcessusMensuel processus) {
        return String.format("%02d/%d", processus.getMoisPaiement(), processus.getAnneePaiement());
    }

    private String libelleJournee(LigneSituee situee) {
        LocalDate dateJour = situee.dateJour();
        if (dateJour != null) {
            return dateJour.format(JOUR);
        }
        Long idFiche = situee.journee().idFicheJournaliere();
        return "journee sans date" + (idFiche == null ? "" : " (fiche n° " + idFiche + ")");
    }

    private String referenceLigne(LigneSituee situee) {
        Long id = situee.ligne().id();
        String jour = situee.dateJour() == null ? "journee sans date" : situee.dateJour().format(JOUR);
        return id == null ? "ligne sans identifiant du " + jour : "ligne n° " + id + " du " + jour;
    }

    private String libelleBeneficiaire(EtatConsolide.Beneficiaire beneficiaire) {
        String nom = beneficiaire.nom() == null ? "" : beneficiaire.nom().strip();
        String prenom = beneficiaire.prenom() == null ? "" : beneficiaire.prenom().strip();
        if (nom.isEmpty() && prenom.isEmpty()) {
            return "beneficiaire n° " + beneficiaire.id();
        }
        if (prenom.isEmpty()) {
            return nom;
        }
        if (nom.isEmpty()) {
            return prenom;
        }
        return nom + " " + prenom;
    }

    /**
     * Enumere les exemples, tronques au-dela de {@link #EXEMPLES_MAX}. Le nombre
     * total reste annonce en tete du message, de sorte que la troncature ne cache
     * jamais l'ampleur du probleme.
     */
    private String enumerer(List<String> elements) {
        if (elements.size() <= EXEMPLES_MAX) {
            return String.join(", ", elements);
        }
        return String.join(", ", elements.subList(0, EXEMPLES_MAX))
                + " et " + (elements.size() - EXEMPLES_MAX) + " autre(s)";
    }

}
