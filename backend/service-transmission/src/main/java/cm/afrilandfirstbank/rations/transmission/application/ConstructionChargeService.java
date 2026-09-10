package cm.afrilandfirstbank.rations.transmission.application;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import cm.afrilandfirstbank.rations.transmission.application.ResultatConstruction.ChargeConstruite;
import cm.afrilandfirstbank.rations.transmission.application.ResultatConstruction.ChargeRefusee;
import cm.afrilandfirstbank.rations.transmission.domaine.CodeAnomalieEnum;
import cm.afrilandfirstbank.rations.transmission.domaine.EtatValideEvent;
import cm.afrilandfirstbank.rations.transmission.domaine.EtatValideEvent.LigneEtat;
import cm.afrilandfirstbank.rations.transmission.domaine.EtatValideEvent.Periode;

/**
 * Assemble la charge de {@code rations.etat.valide} a partir de ses deux sources, et
 * <b>refuse de la laisser partir</b> si elle n'est pas complete et coherente
 * (contrat d'API section 7.1, CT-21).
 *
 * <h2>C'est le dernier filet, et il n'y en a pas d'autre apres</h2>
 *
 * <p>Une fois l'evenement publie, le module n'a aucun moyen de le rattraper : le
 * module de comptabilisation fabriquera ses ecritures a partir de ce qu'il a recu, et
 * l'equipe n'y a pas acces. Une donnee manquante ou fausse produit un paiement errone.
 * D'ou un controle qui <b>refuse plutot que d'approcher</b> : aucune valeur de repli,
 * aucune ligne ecartee pour laisser passer les autres, aucun total recalcule pour le
 * faire coincider.
 *
 * <h2>Les deux codes ne sont pas au meme niveau</h2>
 *
 * <p>{@code codeUnite} est lu sur l'<b>en-tete du processus</b> et pose a la racine :
 * c'est l'unite qui supporte la charge, la ligne de <i>debit</i>. {@code codeAgence}
 * est lu sur le <b>beneficiaire de chaque ligne</b> et pose sur la ligne : c'est
 * l'agence de domiciliation du compte credite, la ligne de <i>credit</i>. Ils viennent
 * de deux objets differents et vont a deux endroits differents ; les intervertir
 * demanderait de croiser deux sources qui ne se rencontrent nulle part ici.
 *
 * <h2>Trois temoins pour un seul montant</h2>
 *
 * <p>Le total est confronte a <b>trois</b> valeurs qui doivent coincider : le
 * {@code montant_total} enregistre sur le processus a la soumission, le
 * {@code montantTotalFcfa} recalcule a l'instant par le service Saisie, et la somme des
 * lignes reellement mises dans la charge. Deux suffiraient a detecter un ecart ; la
 * troisieme dit <i>de quel cote</i> il est. Aucun des trois n'est ajuste sur les
 * autres — on refuse.
 *
 * <h2>Une anomalie par controle</h2>
 *
 * <p>Jamais une par ligne fautive. Trois cents lignes sans code agence produisent une
 * anomalie qui en nomme cinq et compte le reste : une liste se lit, une avalanche ne se
 * lit pas. Meme discipline que le champ {@code manques} du Sprint 4.2.
 *
 * <h2>Ce que cette classe ne fait pas</h2>
 *
 * <p>Elle ne publie rien, ne lit aucune base, n'appelle aucun service et ne produit
 * <b>aucune ecriture comptable</b> : ni compte general, ni sens, ni journal, ni piece.
 * Le schema debit-credit des specifications est informatif (CLAUDE.md section 8). Elle
 * transforme deux lectures en un releve, et dit si ce releve tient debout.
 */
@Service
public class ConstructionChargeService {

    /**
     * Domaine de {@code nature} (RG-01). Chaine et non enumeration : voir
     * {@link EtatValideEvent}. Le nul est teste separement — {@code Set.of(...)} leve sur
     * {@code contains(null)}, et une valeur absente doit produire une anomalie lisible,
     * pas une exception dans le controle cense l'attraper.
     */
    private static final Set<String> NATURES_ADMISES = Set.of("RATION", "TRANSPORT");

    /** Domaine de {@code session} (RG-02). */
    private static final Set<String> SESSIONS_ADMISES = Set.of("JOUR", "SOIR");

    /**
     * Construit la charge, puis la soumet au controle de completude et de coherence.
     *
     * <p><b>Construire d'abord, controler ensuite</b>, et non l'inverse : le controle
     * porte alors sur exactement ce qui partirait, et non sur des donnees d'entree dont
     * il faudrait supposer qu'elles ont ete recopiees sans faute. Une inversion des deux
     * codes, par exemple, echapperait entierement a un controle des seules entrees.
     *
     * @param enTete en-tete lu au service Workflow — periode, unite, type, montant total
     * @param etat detail consolide lu au service Saisie — les lignes et leurs beneficiaires
     * @return {@link ChargeConstruite} si et seulement si la charge peut partir telle
     *         quelle ; {@link ChargeRefusee} sinon, avec toutes les raisons relevees
     */
    public ResultatConstruction construire(EnTeteProcessus enTete, EtatConsolide etat) {
        Objects.requireNonNull(enTete, "en-tete du processus");
        Objects.requireNonNull(etat, "etat consolide");

        EtatValideEvent charge = assembler(enTete, etat);
        List<AnomalieCharge> anomalies = controler(charge, enTete, etat);

        return anomalies.isEmpty()
                ? new ChargeConstruite(charge)
                : new ChargeRefusee(anomalies);
    }

    // --- Assemblage --------------------------------------------------------------

    /**
     * Met les deux lectures a plat dans la forme du contrat section 7.1.
     *
     * <p><b>Aucune valeur n'est inventee.</b> Une valeur absente a la source reste
     * absente ici — un nombre manquant devient {@code 0}, que le controle refuse ensuite
     * comme il refuse un zero. La confusion est sans consequence <i>ici</i>, les deux
     * etant refuses ; elle ne l'est pas ailleurs dans le module, ou un montant absent lu
     * comme zero commanderait le meme aiguillage qu'un etat vide, a tort (Sprint 4.2).
     * Le message d'anomalie nomme donc les deux possibilites.
     *
     * <p><b>Les lignes sont mises a plat</b>, toutes journees confondues, sans date : le
     * contrat ne la prevoit pas et CT-21 ne la demande pas. Une journee ouverte sans
     * ligne disparait naturellement — elle n'engage aucun paiement.
     */
    private EtatValideEvent assembler(EnTeteProcessus enTete, EtatConsolide etat) {
        List<LigneEtat> lignes = journees(etat).stream()
                .flatMap(journee -> lignes(journee).stream())
                .map(this::assemblerLigne)
                .toList();

        return new EtatValideEvent(
                enTete.idProcessus(),
                new Periode(enTete.moisPaiement(), enTete.anneePaiement()),
                // Racine : l'unite qui supporte la charge (ligne de DEBIT).
                enTete.codeUnite(),
                enTete.typeProcessus(),
                enTete.montantTotal() == null ? 0L : enTete.montantTotal().longValue(),
                lignes);
    }

    private LigneEtat assemblerLigne(EtatConsolide.Ligne ligne) {
        EtatConsolide.Beneficiaire beneficiaire = ligne.beneficiaire();

        return new LigneEtat(
                beneficiaire == null ? null : beneficiaire.nom(),
                beneficiaire == null ? null : beneficiaire.prenom(),
                beneficiaire == null ? null : beneficiaire.numCompteCourant(),
                // Ligne : l'agence de domiciliation du compte credite (ligne de CREDIT).
                beneficiaire == null ? null : beneficiaire.codeAgence(),
                ligne.nature(),
                ligne.session(),
                ligne.montantApplique() == null ? 0 : ligne.montantApplique());
    }

    // --- Controle ----------------------------------------------------------------

    private List<AnomalieCharge> controler(EtatValideEvent charge, EnTeteProcessus enTete,
            EtatConsolide etat) {

        List<AnomalieCharge> anomalies = new ArrayList<>();

        controlerRacine(charge, anomalies);
        controlerLignes(charge, anomalies);
        controlerCoherence(charge, etat, anomalies);
        controlerConcordanceDesSources(enTete, etat, anomalies);

        return List.copyOf(anomalies);
    }

    private void controlerRacine(EtatValideEvent charge, List<AnomalieCharge> anomalies) {
        if (charge.idProcessus() == null) {
            anomalies.add(new AnomalieCharge(CodeAnomalieEnum.IDENTIFIANT_ABSENT,
                    "La charge ne porte aucun identifiant de processus : la comptabilite ne "
                            + "pourrait pas en accuser reception."));
        }

        Periode periode = charge.periode();
        if (periode == null || periode.mois() == null || periode.annee() == null
                || periode.mois() < 1 || periode.mois() > 12) {
            anomalies.add(new AnomalieCharge(CodeAnomalieEnum.PERIODE_INVALIDE,
                    "Periode d'imputation inexploitable (" + libellePeriode(periode)
                            + ") : la comptabilite ne saurait pas sur quelle periode imputer."));
        }

        if (estVide(charge.codeUnite())) {
            anomalies.add(new AnomalieCharge(CodeAnomalieEnum.CODE_UNITE_ABSENT,
                    "Aucun code unite a la racine de la charge : l'unite qui supporte la "
                            + "depense — la ligne de debit — serait indeterminee."));
        }

        if (estVide(charge.typeProcessus())) {
            anomalies.add(new AnomalieCharge(CodeAnomalieEnum.TYPE_PROCESSUS_ABSENT,
                    "Type de processus absent : un etat normal et une regularisation ne se "
                            + "traitent pas de la meme facon en aval."));
        }

        if (charge.lignes() == null || charge.lignes().isEmpty()) {
            anomalies.add(new AnomalieCharge(CodeAnomalieEnum.CHARGE_SANS_LIGNE,
                    "Aucune ligne de prestation : un etat vide n'engage aucun paiement et n'a "
                            + "rien a faire en comptabilite."));
        }

        if (charge.montantTotal() <= 0) {
            anomalies.add(new AnomalieCharge(CodeAnomalieEnum.MONTANT_TOTAL_INVALIDE,
                    "Montant total absent, nul ou negatif (" + charge.montantTotal()
                            + " FCFA) : rien de payable ne se presente ainsi."));
        }
    }

    /**
     * Une passe sur les lignes, six controles, <b>une anomalie par controle</b> — jamais
     * une par ligne. Chaque anomalie nomme jusqu'a cinq lignes fautives et annonce le
     * reste en nombre.
     */
    private void controlerLignes(EtatValideEvent charge, List<AnomalieCharge> anomalies) {
        List<LigneEtat> lignes = charge.lignes() == null ? List.of() : charge.lignes();

        releverSurLignes(lignes, anomalies, CodeAnomalieEnum.LIGNE_SANS_BENEFICIAIRE,
                ligne -> estVide(ligne.nom()) && estVide(ligne.prenom()),
                "ligne(s) sans beneficiaire identifiable : l'ordre de paiement serait anonyme",
                ligne -> "compte " + affiche(ligne.numCompteCourant()));

        releverSurLignes(lignes, anomalies, CodeAnomalieEnum.LIGNE_SANS_COMPTE,
                ligne -> estVide(ligne.numCompteCourant()),
                "ligne(s) sans numero de compte courant : la ligne de credit serait impossible "
                        + "a produire",
                this::identiteLigne);

        releverSurLignes(lignes, anomalies, CodeAnomalieEnum.LIGNE_SANS_CODE_AGENCE,
                ligne -> estVide(ligne.codeAgence()),
                "ligne(s) sans code agence : la comptabilite ne saurait pas ou porter le credit "
                        + "du beneficiaire",
                this::identiteLigne);

        releverSurLignes(lignes, anomalies, CodeAnomalieEnum.LIGNE_SANS_MONTANT,
                ligne -> ligne.montant() <= 0,
                "ligne(s) sans montant exploitable — absent, nul ou negatif —, ce que RG-03 "
                        + "exclut : le montant vient de la grille active",
                this::identiteLigne);

        releverSurLignes(lignes, anomalies, CodeAnomalieEnum.LIGNE_NATURE_INCONNUE,
                ligne -> ligne.nature() == null || !NATURES_ADMISES.contains(ligne.nature()),
                "ligne(s) dont la nature n'est ni RATION ni TRANSPORT (RG-01)",
                ligne -> identiteLigne(ligne) + ", nature " + affiche(ligne.nature()));

        releverSurLignes(lignes, anomalies, CodeAnomalieEnum.LIGNE_SESSION_INCONNUE,
                ligne -> ligne.session() == null || !SESSIONS_ADMISES.contains(ligne.session()),
                "ligne(s) dont la session n'est ni JOUR ni SOIR (RG-02)",
                ligne -> identiteLigne(ligne) + ", session " + affiche(ligne.session()));
    }

    /**
     * Le controle central : le montant total doit valoir la somme des lignes.
     *
     * <p>Somme en {@code long} sur des {@code int}, sans arrondi possible. Le total rendu
     * par le service Saisie est confronte au meme titre : les trois valeurs doivent
     * coincider, et le message les nomme toutes les trois pour dire de quel cote est
     * l'ecart.
     *
     * <p><b>Aucun ajustement.</b> Recalculer le total sur la somme des lignes ferait
     * disparaitre l'ecart sans le resoudre — et transmettrait un montant que personne
     * n'a valide ni signe.
     */
    private void controlerCoherence(EtatValideEvent charge, EtatConsolide etat,
            List<AnomalieCharge> anomalies) {

        List<LigneEtat> lignes = charge.lignes() == null ? List.of() : charge.lignes();
        long sommeDesLignes = lignes.stream().mapToLong(LigneEtat::montant).sum();

        Long totalSaisie = etat.montantTotalFcfa();
        boolean totalSaisieConcorde = totalSaisie != null && totalSaisie == sommeDesLignes;

        if (charge.montantTotal() != sommeDesLignes || !totalSaisieConcorde) {
            anomalies.add(new AnomalieCharge(CodeAnomalieEnum.TOTAL_INCOHERENT,
                    "Le montant total ne coincide pas avec le detail : " + charge.montantTotal()
                            + " FCFA porte par le processus, " + affiche(totalSaisie)
                            + " FCFA rendu par le service Saisie, " + sommeDesLignes
                            + " FCFA en sommant les " + lignes.size() + " ligne(s) de la charge. "
                            + "Publication refusee : un total faux est un paiement faux, et "
                            + "l'evenement ne se rattrape pas une fois parti."));
        }
    }

    /**
     * Les deux lectures doivent parler du meme dossier.
     *
     * <p>Un ecart de periode ou d'unite entre l'en-tete et le detail signale un
     * identifiant croise ou une reponse mal rattachee — jamais un cas metier. Publier
     * quand meme imputerait les lignes d'une unite sur le budget d'une autre.
     */
    private void controlerConcordanceDesSources(EnTeteProcessus enTete, EtatConsolide etat,
            List<AnomalieCharge> anomalies) {

        // Le service Saisie rend une unite et une periode nulles sur un etat dont aucune
        // fiche n'a ete ouverte : il n'a alors rien a rapprocher. Ce cas est deja refuse
        // par CHARGE_SANS_LIGNE, inutile de le signaler une seconde fois.
        boolean detailMuet = etat.codeUnite() == null && etat.dateDebut() == null
                && etat.dateFin() == null;
        if (detailMuet) {
            return;
        }

        boolean uniteConcorde = Objects.equals(enTete.codeUnite(), etat.codeUnite());
        // Comparaison sur les BORNES depuis la Maille 1, et non plus sur le mois
        // derive. Les deux sources portent desormais l'intervalle ; comparer le mois
        // derive laisserait passer pour concordantes deux periodes differentes d'un
        // meme mois — et ferait echouer la concordance des que l'une des deux vaut
        // null parce que la periode chevauche deux mois.
        boolean periodeConcorde = Objects.equals(enTete.dateDebut(), etat.dateDebut())
                && Objects.equals(enTete.dateFin(), etat.dateFin());

        if (!uniteConcorde || !periodeConcorde) {
            anomalies.add(new AnomalieCharge(CodeAnomalieEnum.SOURCES_DISCORDANTES,
                    "L'en-tete et le detail ne portent pas sur le meme dossier : le processus "
                            + "est declare unite " + affiche(enTete.codeUnite()) + " periode "
                            + libellePeriode(enTete.dateDebut(), enTete.dateFin())
                            + ", tandis que le detail rendu par le service Saisie porte unite "
                            + affiche(etat.codeUnite()) + " periode "
                            + libellePeriode(etat.dateDebut(), etat.dateFin()) + "."));
        }
    }

    // --- Outillage ---------------------------------------------------------------

    /**
     * Applique un controle a toutes les lignes et n'en tire qu'<b>une</b> anomalie.
     *
     * @param fautive predicat de non-conformite
     * @param constat texte du controle, precede du nombre de lignes fautives
     * @param exemple comment nommer une ligne fautive dans les exemples
     */
    private void releverSurLignes(List<LigneEtat> lignes, List<AnomalieCharge> anomalies,
            CodeAnomalieEnum code, Predicate<LigneEtat> fautive,
            String constat, Function<LigneEtat, String> exemple) {

        List<LigneEtat> fautives = lignes.stream().filter(fautive).toList();
        if (fautives.isEmpty()) {
            return;
        }

        anomalies.add(new AnomalieCharge(code,
                fautives.size() + " " + constat + " : " + exemples(fautives, exemple) + "."));
    }

    private String exemples(List<LigneEtat> fautives, Function<LigneEtat, String> exemple) {
        String nommes = fautives.stream()
                .limit(AnomalieCharge.EXEMPLES_MAXIMUM)
                .map(exemple)
                .collect(Collectors.joining(", "));

        int reste = fautives.size() - Math.min(fautives.size(), AnomalieCharge.EXEMPLES_MAXIMUM);
        return reste == 0 ? nommes : nommes + ", et " + reste + " autre(s)";
    }

    private String identiteLigne(LigneEtat ligne) {
        String nom = (affiche(ligne.nom()) + " " + affiche(ligne.prenom())).trim();
        return nom.isBlank() ? "beneficiaire non identifie" : nom;
    }

    private static boolean estVide(String valeur) {
        return valeur == null || valeur.isBlank();
    }

    private static String affiche(Object valeur) {
        return valeur == null ? "(absent)" : String.valueOf(valeur);
    }

    private static String libellePeriode(Periode periode) {
        return periode == null ? "(absente)" : libellePeriode(periode.mois(), periode.annee());
    }

    private static String libellePeriode(LocalDate dateDebut, LocalDate dateFin) {
        return "du " + affiche(dateDebut) + " au " + affiche(dateFin);
    }

    private static String libellePeriode(Integer mois, Integer annee) {
        return affiche(mois) + "/" + affiche(annee);
    }

    private List<EtatConsolide.Journee> journees(EtatConsolide etat) {
        return etat.journees() == null ? List.of() : etat.journees();
    }

    private List<EtatConsolide.Ligne> lignes(EtatConsolide.Journee journee) {
        return journee.lignes() == null ? List.of() : journee.lignes();
    }

}
