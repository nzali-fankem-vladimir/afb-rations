package cm.afrilandfirstbank.rations.transmission.domaine;

import java.util.List;

/**
 * Charge publiee sur {@code rations.etat.valide} a la cloture d'un etat
 * (contrat d'API section 7.1, US-12, CT-21).
 *
 * <h2>Ce que ce document est, et ce qu'il n'est pas</h2>
 *
 * <p>C'est un <b>releve de ce qui a ete valide</b> : qui a ete servi, de quoi, pour
 * combien, sur quelle periode et a la charge de quelle unite. Ce n'est <b>pas</b> une
 * ecriture comptable. Le module de comptabilisation, auquel l'equipe n'a pas acces,
 * fabrique les ecritures et gere l'impact CBS ; le schema debit-credit figurant en
 * annexe des specifications est informatif et ne se code pas ici (CLAUDE.md
 * section 8). On ne trouvera donc dans ce type ni compte general, ni sens, ni
 * journal, ni piece comptable.
 *
 * <h2>Les deux codes ne sont pas au meme niveau, et ce n'est pas un detail</h2>
 *
 * <p>{@code codeUnite} est <b>a la racine</b> : c'est l'unite qui supporte la charge,
 * la ligne de <i>debit</i>. {@code codeAgence} est <b>sur chaque ligne</b> : c'est
 * l'agence de domiciliation du compte du beneficiaire, la ligne de <i>credit</i>.
 * Les deux partagent le format et le referentiel des codes guichets Afriland
 * (cinq chiffres), ce qui les rend indiscernables a l'oeil : une inversion ne
 * produirait aucune erreur, seulement une operation qui debiterait et crediterait les
 * mauvaises entites. En omettre un rendrait l'ecriture irreconstituable cote
 * comptabilite (CLAUDE.md section 4).
 *
 * <p>C'est aussi pourquoi il n'existe pas de constructeur qui prendrait les deux
 * codes cote a cote : ils vivent dans deux enregistrements differents, et le
 * compilateur ne peut pas les confondre.
 *
 * <h2>Aucune date de journee</h2>
 *
 * <p>Les lignes sont <b>a plat</b>, sans decoupage par journee : ni {@code dateJour}
 * ni identifiant de fiche. Le contrat section 7.1 ne les prevoit pas, et CT-21 ne les
 * demande pas — la comptabilite raisonne sur la periode mensuelle, pas sur le jour de
 * service. Les enrichir de leur cote reviendrait a inventer un contrat que le module
 * receveur ne lit pas. La tracabilite journaliere reste entiere du cote du module,
 * dans {@code fiche_journaliere} et dans le journal d'audit.
 *
 * <h2>Montants entiers</h2>
 *
 * <p>{@code int} par ligne, {@code long} pour le total : aucun flottant, aucun
 * {@code BigDecimal} (CLAUDE.md section 4, montants en FCFA entiers). Un arrondi
 * suffirait a decaler d'un franc un montant qui part en paiement.
 *
 * <p>{@code nature} et {@code session} voyagent en <b>chaine</b> et non en
 * enumeration : ce sont les valeurs telles que le service Saisie les a figees, et une
 * enumeration ferait echouer la serialisation le jour ou une valeur inconnue
 * apparaitrait — au lieu de la transmettre et de la laisser voir. Leur domaine est
 * verifie par le controle de completude, qui refuse la publication plutot que de
 * l'echouer.
 */
public record EtatValideEvent(
        Long idProcessus,
        Periode periode,
        String codeUnite,
        String typeProcessus,
        long montantTotal,
        List<LigneEtat> lignes) {

    /** Periode de paiement de l'etat : mois de 1 a 12, et son annee. */
    public record Periode(Integer mois, Integer annee) {
    }

    /**
     * Une prestation servie a un beneficiaire.
     *
     * @param numCompteCourant compte a crediter. Sans lui la comptabilite ne peut
     *        produire aucune ligne de credit : le controle de completude le refuse
     * @param codeAgence agence de <b>domiciliation du compte du beneficiaire</b>, a
     *        ne jamais confondre avec le {@code codeUnite} de la racine
     * @param montant montant figé a la saisie depuis la grille active (RG-03), jamais
     *        recalcule ici
     */
    public record LigneEtat(
            String nom,
            String prenom,
            String numCompteCourant,
            String codeAgence,
            String nature,
            String session,
            int montant) {
    }

}
