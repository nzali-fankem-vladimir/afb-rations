package cm.afrilandfirstbank.rations.workflow.application;

import org.springframework.stereotype.Service;

import cm.afrilandfirstbank.rations.workflow.domaine.ProcessusMensuel;
import cm.afrilandfirstbank.rations.workflow.domaine.TypeProcessusEnum;
import cm.afrilandfirstbank.rations.workflow.domaine.exception.SeuilIndisponibleException;

/**
 * RG-08 : apres la validation du Chef d'Unite, le montant decide seul du niveau
 * d'approbation restant.
 *
 * <pre>
 *   etat NORMAL, montant &lt;= seuil  -&gt; SOUS_SEUIL_CLOTURE_DIRECTE    clos (CT-14)
 *   etat NORMAL, montant &gt;  seuil  -&gt; ENVOI_DIRECTEUR_RESEAU        monte au DR (CT-15)
 *   etat COMPLEMENTAIRE, tout montant
 *                                  -&gt; COMPLEMENTAIRE_ENVOI_DIRECTEUR_RESEAU
 *                                     monte au DR, seuil NON lu (Sprint 6bis.1)
 * </pre>
 *
 * <h2>L'etat complementaire monte toujours, en attendant l'arbitrage du metier</h2>
 *
 * <p>Regle <b>provisoire</b>, retenue au Sprint 6bis.1 avant que le metier ne se
 * prononce : une regularisation sur une periode close exige le second niveau
 * d'approbation, quel que soit son montant.
 *
 * <p>Elle n'est pas neutre, et c'est voulu. Les deux autres lectures possibles
 * laissaient chacune un trou : comparer le montant du complementaire au seuil, comme
 * pour un etat normal, permettait de fractionner une regularisation en plusieurs
 * etats restant chacun sous la barre ; le laisser clore par le seul Chef d'Unite
 * autorisait un complementaire de n'importe quel montant sans second regard. La
 * regle retenue ne peut, elle, que <b>trop</b> demander — et c'est le seul sens dans
 * lequel on se trompe sans consequence sur un paiement.
 *
 * <p>Si le metier arbitre autrement, la bascule tient dans ce fichier : la
 * comparaison de RG-08 n'existe qu'ici. Voir
 * {@code docs/decisions/2026-09-05-ouverture-etat-complementaire-et-drapeau.md} § 8.
 *
 * <h2>Une seule comparaison, dans tout le module</h2>
 *
 * <p>{@link #aiguiller(ProcessusMensuel)} contient la seule ligne du service
 * Workflow qui compare un montant a un seuil. Ni {@code ValidationService}, ni le
 * controleur, ni la machine a etats n'en refont une : ils recoivent une decision
 * deja prise et l'appliquent. <b>Une comparaison qui n'existe qu'a un endroit ne
 * peut pas diverger d'elle-meme</b>, et une revue de RG-08 se fait en lisant un
 * seul fichier.
 *
 * <h2>Le sens est porte par les noms, pas par l'operateur</h2>
 *
 * <p>La comparaison s'ecrit {@code montantTotal > seuil} et rend
 * {@link DecisionAiguillage#ENVOI_DIRECTEUR_RESEAU} : « strictement superieur au
 * seuil, donc au Directeur Reseau », qui est mot pour mot la phrase de RG-08. Un
 * {@code >=} pose la par distraction devient <b>visiblement</b> faux a la
 * relecture, pas seulement faux a l'execution — c'est ce qui distingue une erreur
 * qu'on trouve d'une erreur qu'on subit.
 *
 * <p>Et la preuve par les tests n'est pas faite de trois points isoles :
 * {@code AiguillageServiceTest} balaye une plage de montants autour du seuil et
 * exige que la decision <b>bascule exactement une fois</b>, au passage de
 * {@code seuil} a {@code seuil + 1}. Une inversion, un decalage d'une unite ou une
 * comparaison retournee deplacent ce point de bascule et font tomber le test, meme
 * si les cas nommes avaient ete « ajustes » en meme temps.
 *
 * <h2>Ce service ne modifie rien</h2>
 *
 * <p>Il ne touche ni au statut, ni a la base, ni au document : il repond a une
 * question. C'est ce qui permet de l'eprouver isolement, sur des montants precis,
 * sans monter un processus complet — et c'est ce qui rend les tests de borne
 * lisibles.
 *
 * <h2>Le montant compare est celui qui est enregistre</h2>
 *
 * <p>{@code processus_mensuel.montant_total}, reporte a la soumission depuis l'etat
 * consolide (Sprint 4.2). <b>Jamais un montant redemande au service Saisie a
 * l'instant de la validation</b> : une grille modifiee entretemps ferait alors
 * dependre l'aiguillage d'un evenement etranger au dossier, et le chef d'unite
 * validerait un montant different de celui qu'il a lu.
 */
@Service
public class AiguillageService {

    private final SeuilService seuilService;

    public AiguillageService(SeuilService seuilService) {
        this.seuilService = seuilService;
    }

    /**
     * Decide de l'issue apres la validation du Chef d'Unite.
     *
     * @param processus l'etat valide, portant le montant total enregistre a la
     *        soumission
     * @return la decision, accompagnee du montant et du seuil qui l'ont produite
     * @throws SeuilIndisponibleException si le seuil n'est pas lisible. <b>Aucun
     *         repli</b> : sans seuil, il n'y a pas d'aiguillage possible, et en
     *         inventer un reviendrait a decider seul du niveau d'approbation requis
     */
    public ResultatAiguillage aiguiller(ProcessusMensuel processus) {
        if (processus == null) {
            throw new IllegalArgumentException(
                    "Aucun processus fourni a l'aiguillage : RG-08 porte sur un montant "
                            + "enregistre, il n'y a rien a comparer.");
        }

        // Un etat COMPLEMENTAIRE monte au Directeur Reseau quel que soit son montant :
        // il n'y a rien a comparer, et le seuil n'est donc PAS lu. Le lire quand meme
        // ferait echouer une validation sur un parametre qui ne la gouverne pas — le
        // meme defaut que rappeler l'aiguillage au second niveau (Sprint 4.4).
        if (processus.getTypeProcessus() == TypeProcessusEnum.COMPLEMENTAIRE) {
            return new ResultatAiguillage(
                    DecisionAiguillage.COMPLEMENTAIRE_ENVOI_DIRECTEUR_RESEAU,
                    processus.getMontantTotal(),
                    null);
        }

        // Le seuil est relu ici, a chaque aiguillage : une modification en base prend
        // effet a la validation suivante, sans redemarrage ni delai (CT-18).
        long seuil = seuilService.seuilAiguillage();

        // Comparaison en long des deux cotes : montant_total est un INTEGER en base,
        // le seuil peut etre plus large ; aucun debordement ne peut inverser la
        // decision.
        long montantTotal = processus.getMontantTotal();

        DecisionAiguillage decision = montantTotal > seuil
                ? DecisionAiguillage.ENVOI_DIRECTEUR_RESEAU
                : DecisionAiguillage.SOUS_SEUIL_CLOTURE_DIRECTE;

        return new ResultatAiguillage(decision, montantTotal, seuil);
    }

}
