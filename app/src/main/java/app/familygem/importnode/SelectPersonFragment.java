package app.familygem.importnode;

import static app.familygem.Global.gc;

import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.StateListDrawable;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.SubMenu;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Filter;
import android.widget.Filterable;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SearchView;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.RecyclerView;

import com.lb.fast_scroller_and_recycler_view_fixes_library.FastScrollerEx;

import org.folg.gedcom.model.EventFact;
import org.folg.gedcom.model.Family;
import org.folg.gedcom.model.Name;
import org.folg.gedcom.model.Person;
import org.joda.time.Days;
import org.joda.time.LocalDate;
import org.joda.time.Years;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import app.familygem.Datatore;
import app.familygem.EditaIndividuo;
import app.familygem.F;
import app.familygem.Global;
import app.familygem.R;
import app.familygem.U;
import app.familygem.constants.Format;
import app.familygem.constants.Gender;

public class SelectPersonFragment extends Fragment {
    List<Person> people;
    AdattatoreAnagrafe adapter;
    private Order order = Order.NONE;
    private boolean gliIdsonoNumerici;

    private enum Order {
        NONE,
        ID_ASC, ID_DESC,
        SURNAME_ASC, SURNAME_DESC,
        DATE_ASC, DATE_DESC,
        AGE_ASC, AGE_DESC,
        KIN_ASC, KIN_DESC;
        public Order next() {
            return values()[ordinal() + 1];
        }
        public Order prev() {
            return values()[ordinal() - 1];
        }
    };
    private SelectPersonViewModel viewModel;

    private PersonSelectedCallback globalPersonSelectedCallback = new PersonSelectedCallback() {
        @Override
        public void onPersonSelected(Person person) {
            if(viewModel != null){
                viewModel.setPerson(person.getId(), U.epiteto(person));
            }
        }
    };


    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle bundle) {
        View view = inflater.inflate(R.layout.ricicla_vista, container, false);
        View fabBox = view.findViewById((R.id.fab_box));
        fabBox.setVisibility(View.GONE);

        if( gc != null ) {
            people = gc.getPeople();
            arredaBarra();
            RecyclerView vistaLista = view.findViewById(R.id.riciclatore);
            vistaLista.setPadding(12, 12, 12, vistaLista.getPaddingBottom());
            adapter = new AdattatoreAnagrafe(globalPersonSelectedCallback);
            adapter.personSelectedCallback = globalPersonSelectedCallback;
            vistaLista.setAdapter(adapter);
            gliIdsonoNumerici = verificaIdNumerici();
            view.findViewById(R.id.fab).setOnClickListener(v -> {
                Intent intent = new Intent(getContext(), EditaIndividuo.class);
                intent.putExtra("idIndividuo", "TIZIO_NUOVO");
                startActivity(intent);
            });

            // Fast scroller
            StateListDrawable thumbDrawable = (StateListDrawable) ContextCompat.getDrawable(getContext(), R.drawable.scroll_thumb);
            Drawable lineDrawable = ContextCompat.getDrawable(getContext(), R.drawable.empty);
            new FastScrollerEx(vistaLista, thumbDrawable, lineDrawable, thumbDrawable, lineDrawable,
                    U.dpToPx(40), U.dpToPx(100), 0, true, U.dpToPx(80));
        }
        return view;
    }

    @Override
    public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
        viewModel = new ViewModelProvider(requireActivity()).get(SelectPersonViewModel.class);
    }

    void arredaBarra() {
        ((AppCompatActivity)getActivity()).getSupportActionBar().setTitle(people.size() + " "
                + getString(people.size() == 1 ? R.string.person : R.string.persons).toLowerCase());
        setHasOptionsMenu(people.size() > 1);
    }

    public class AdattatoreAnagrafe extends RecyclerView.Adapter<GestoreIndividuo> implements Filterable {
        private PersonSelectedCallback personSelectedCallback = null;

        public AdattatoreAnagrafe(PersonSelectedCallback personSelectedCallback){
            this.personSelectedCallback = personSelectedCallback;
        }

        @Override
        public GestoreIndividuo onCreateViewHolder(ViewGroup parent, int tipo) {
            View vistaIndividuo = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.pezzo_individuo, parent, false);
            registerForContextMenu(vistaIndividuo);
            return new GestoreIndividuo(vistaIndividuo, personSelectedCallback);
        }

        @Override
        public void onBindViewHolder(GestoreIndividuo gestore, int posizione) {
            Person person = people.get(posizione);
            View vistaIndi = gestore.vista;

            String label = null;
            if( order == Order.ID_ASC || order == Order.ID_DESC )
                label = person.getId();
            else if( order == Order.KIN_ASC || order == Order.KIN_DESC )
                label = String.valueOf(person.getExtension("kin"));
            TextView vistaRuolo = vistaIndi.findViewById(R.id.indi_ruolo);
            // don't show field "kin" because it is not maintained in github to avoid conflict
//			if( label == null )
//				vistaRuolo.setVisibility(View.GONE);
//			else {
//				vistaRuolo.setText(label);
//				vistaRuolo.setVisibility(View.VISIBLE);
//			}

            TextView vistaNome = vistaIndi.findViewById(R.id.indi_nome);
            String nome = U.epiteto(person);
            vistaNome.setText(nome);
            vistaNome.setVisibility((nome.isEmpty() && label != null) ? View.GONE : View.VISIBLE);

            TextView vistaTitolo = vistaIndi.findViewById(R.id.indi_titolo);
            String titolo = U.titolo(person);
            if( titolo.isEmpty() )
                vistaTitolo.setVisibility(View.GONE);
            else {
                vistaTitolo.setText(titolo);
                vistaTitolo.setVisibility(View.VISIBLE);
            }

            int bordo;
            switch( Gender.getGender(person) ) {
                case MALE: bordo = R.drawable.casella_bordo_maschio; break;
                case FEMALE: bordo = R.drawable.casella_bordo_femmina; break;
                default: bordo = R.drawable.casella_bordo_neutro;
            }
            vistaIndi.findViewById(R.id.indi_bordo).setBackgroundResource(bordo);

            U.details(person, vistaIndi.findViewById(R.id.indi_dettagli));
            F.unaFoto(Global.gc, person, vistaIndi.findViewById(R.id.indi_foto));
            vistaIndi.findViewById(R.id.indi_lutto).setVisibility(U.isDead(person) ? View.VISIBLE : View.GONE);
            vistaIndi.setTag(person.getId());
        }
        @Override
        public Filter getFilter() {
            return new Filter() {
                @Override
                protected FilterResults performFiltering(CharSequence charSequence) {
                    String query = charSequence.toString();
                    if (query.isEmpty()) {
                        people = gc.getPeople();
                    } else {
                        List<Person> filteredList = new ArrayList<>();
                        for (Person pers : gc.getPeople()) {
                            if( U.epiteto(pers).toLowerCase().contains(query.toLowerCase()) ) {
                                filteredList.add(pers);
                            }
                        }
                        people = filteredList;
                    }
                    sortPeople();
                    FilterResults filterResults = new FilterResults();
                    filterResults.values = people;
                    return filterResults;
                }
                @Override
                protected void publishResults(CharSequence cs, FilterResults fr) {
                    notifyDataSetChanged();
                }
            };
        }
        @Override
        public int getItemCount() {
            return people.size();
        }
    }

    class GestoreIndividuo extends RecyclerView.ViewHolder implements View.OnClickListener {
        View vista;
        private PersonSelectedCallback personSelectedCallback;

        GestoreIndividuo( View vista, PersonSelectedCallback personSelectedCallback) {
            super( vista );
            this.vista = vista;
            this.personSelectedCallback = personSelectedCallback;
            vista.setOnClickListener(this);
        }

        @Override
        public void onClick( View vista ) {
            if(personSelectedCallback != null){
                String tag = (String)vista.getTag();
                Person person = gc.getPerson(tag);
                personSelectedCallback.onPersonSelected(person);
            }

        }
    }

    interface PersonSelectedCallback {
        void onPersonSelected(Person person);
    }

    // Andandosene dall'attività senza aver scelto un parente resetta l'extra
    @Override
    public void onPause() {
        super.onPause();
        getActivity().getIntent().removeExtra("anagrafeScegliParente");
    }

    // Verifica se tutti gli id delle persone contengono numeri
    // Appena un id contiene solo lettere restituisce falso
    boolean verificaIdNumerici() {
        esterno:
        for( Person p : gc.getPeople() ) {
            for( char c : p.getId().toCharArray() ) {
                if (Character.isDigit(c))
                    continue esterno;
            }
            return false;
        }
        return true;
    }

    private void sortPeople() {
        if (order == null) {
            // Handle the null case, e.g., set a default value or throw an exception
            order = Order.NONE; // Assuming DEFAULT_ORDER is a valid default
        }
        Collections.sort(people, (p1, p2) -> {
            switch( order ) {
                case ID_ASC: // Sort for GEDCOM ID
                    if( gliIdsonoNumerici )
                        return U.soloNumeri(p1.getId()) - U.soloNumeri(p2.getId());
                    else
                        return p1.getId().compareToIgnoreCase(p2.getId());
                case ID_DESC:
                    if( gliIdsonoNumerici )
                        return U.soloNumeri(p2.getId()) - U.soloNumeri(p1.getId());
                    else
                        return p2.getId().compareToIgnoreCase(p1.getId());
                case SURNAME_ASC: // Sort for surname
                    if (p1.getNames().size() == 0) // i nomi null vanno in fondo
                        return (p2.getNames().size() == 0) ? 0 : 1;
                    if (p2.getNames().size() == 0)
                        return -1;
                    Name n1 = p1.getNames().get(0);
                    Name n2 = p2.getNames().get(0);
                    // anche i nomi con value, given e surname null vanno in fondo
                    if (n1.getValue() == null && n1.getGiven() == null && n1.getSurname() == null)
                        return (n2.getValue() == null) ? 0 : 1;
                    if (n2.getValue() == null && n2.getGiven() == null && n2.getSurname() == null)
                        return -1;
                    return cognomeNome(p1).compareToIgnoreCase(cognomeNome(p2));
                case SURNAME_DESC:
                    if (p1.getNames().size() == 0)
                        return p2.getNames().size() == 0 ? 0 : 1;
                    if (p2.getNames().size() == 0)
                        return -1;
                    n1 = p1.getNames().get(0);
                    n2 = p2.getNames().get(0);
                    if (n1.getValue() == null && n1.getGiven() == null && n1.getSurname() == null)
                        return (n2.getValue() == null) ? 0 : 1;
                    if (n2.getValue() == null && n2.getGiven() == null && n2.getSurname() == null)
                        return -1;
                    return cognomeNome(p2).compareToIgnoreCase(cognomeNome(p1));
                case DATE_ASC: // Sort for person's main year
                    return getDate(p1) - getDate(p2);
                case DATE_DESC:
                    int date1 = getDate(p1);
                    int date2 = getDate(p2);
                    if( date2 == Integer.MAX_VALUE ) // Those without year go to the bottom
                        return -1;
                    if( date1 == Integer.MAX_VALUE )
                        return date2 == Integer.MAX_VALUE ? 0 : 1;
                    return date2 - date1;
                case AGE_ASC: // Sort for main person's year
                    return getAge(p1) - getAge(p2);
                case AGE_DESC:
                    int age1 = getAge(p1);
                    int age2 = getAge(p2);
                    if( age2 == Integer.MAX_VALUE ) // Those without age go to the bottom
                        return -1;
                    if( age1 == Integer.MAX_VALUE )
                        return age2 == Integer.MAX_VALUE ? 0 : 1;
                    return age2 - age1;
                case KIN_ASC: // Sort for number of relatives
                    return countRelatives(p1) - countRelatives(p2);
                case KIN_DESC:
                    return countRelatives(p2) - countRelatives(p1);
            }
            return 0;
        });
    }

    // Restituisce una stringa con cognome e nome attaccati:
    // 'SalvadorMichele ' oppure 'ValleFrancesco Maria ' oppure ' Donatella '
    static String cognomeNome( Person tizio ) {
        Name name = tizio.getNames().get(0);
        String epiteto = name.getValue();
        String nomeDato = "";
        String cognome = " "; // deve esserci uno spazio per ordinare i nomi senza cognome
        if( epiteto != null ) {
            if( epiteto.indexOf('/') > 0 )
                nomeDato = epiteto.substring( 0, epiteto.indexOf('/') );	// prende il nome prima di '/'
            if( epiteto.lastIndexOf('/') - epiteto.indexOf('/') > 1 )	// se c'è un cognome tra i due '/'
                cognome = epiteto.substring( epiteto.indexOf('/')+1, epiteto.lastIndexOf("/") );
            String prefix = name.getPrefix(); // Solo il nomeDato proveniente dal value potrebbe avere un prefisso, dal given no perché già di suo è solo il nomeDato
            if( prefix != null && nomeDato.startsWith(prefix) )
                nomeDato = nomeDato.substring( prefix.length() ).trim();
        } else {
            if( name.getGiven() != null )
                nomeDato = name.getGiven();
            if( name.getSurname() != null )
                cognome = name.getSurname();
        }
        String surPrefix = name.getSurnamePrefix();
        if( surPrefix != null && cognome.startsWith(surPrefix) )
            cognome = cognome.substring( surPrefix.length() ).trim();
        return cognome.concat( nomeDato );
    }

    // riceve una Person e restituisce il primo anno della sua esistenza
    Datatore datatore = new Datatore("");
    private int findDate(Person person) {
        for( EventFact event : person.getEventsFacts() ) {
            if( event.getDate() != null ) {
                datatore.analizza(event.getDate());
                return datatore.getDateNumber();
            }
        }
        return Integer.MAX_VALUE;
    }

    int getDate(Person person) {
        Object date = person.getExtension("date");
        return date == null ? Integer.MAX_VALUE : (int)date;
    }

    // Calculate the age of a person in days or MAX_VALUE
    private int calcAge(Person person) {
        int days = Integer.MAX_VALUE;
        Datatore start = null, end = null;
        for( EventFact event : person.getEventsFacts() ) {
            if( event.getTag() != null && event.getTag().equals("BIRT") && event.getDate() != null ) {
                start = new Datatore(event.getDate());
                break;
            }
        }
        for( EventFact event : person.getEventsFacts() ) {
            if( event.getTag() != null && event.getTag().equals("DEAT") && event.getDate() != null ) {
                end = new Datatore(event.getDate());
                break;
            }
        }
        if( start != null && start.isSingleKind() && !start.data1.isFormat(Format.D_M) ) {
            LocalDate startDate = new LocalDate(start.data1.date);
            // If the person is still alive the end is now
            LocalDate now = LocalDate.now();
            if( end == null && startDate.isBefore(now)
                    && Years.yearsBetween(startDate, now).getYears() <= 120 && !U.isDead(person) ) {
                end = new Datatore(now.toDate());
            }
            if( end != null && end.isSingleKind() && !end.data1.isFormat(Format.D_M) ) {
                LocalDate endDate = new LocalDate(end.data1.date);
                if( startDate.isBefore(endDate) || startDate.isEqual(endDate) ) {
                    days = Days.daysBetween(startDate, endDate).getDays();
                }
            }
        }
        return days;
    }

    int getAge(Person person) {
        Object age = person.getExtension("age");
        return age == null ? Integer.MAX_VALUE : (int)age;
    }

    // Write the two main places of a person (initial – final) or null
    static String twoPlaces(Person person) {
        List<EventFact> facts = person.getEventsFacts();
        // One single event
        if( facts.size() == 1 ) {
            String place = facts.get(0).getPlace();
            if( place != null )
                return stripCommas(place);
        } // Sex and another event
        else if( facts.size() == 2 && ("SEX".equals(facts.get(0).getTag()) || "SEX".equals(facts.get(1).getTag())) ) {
            String place;
            if( "SEX".equals(facts.get(0).getTag()) )
                place = facts.get(1).getPlace();
            else
                place = facts.get(0).getPlace();
            if( place != null )
                return stripCommas(place);
        } // Multiple events
        else if( facts.size() >= 2 ) {
            String[] places = new String[7];
            for( EventFact ef : facts ) {
                String place = ef.getPlace();
                if( place != null ) {
                    switch( ef.getTag() ) {
                        case "BIRT":
                            places[0] = place;
                            break;
                        case "BAPM":
                            places[1] = place;
                            break;
                        case "DEAT":
                            places[4] = place;
                            break;
                        case "CREM":
                            places[5] = place;
                            break;
                        case "BURI":
                            places[6] = place;
                            break;
                        default:
                            if( places[2] == null ) // First of other events
                                places[2] = place;
                            if( !place.equals(places[2]) )
                                places[3] = place; // Last of other events
                    }
                }
            }
            String text = null;
            int i;
            // Write initial place
            for( i = 0; i < places.length; i++ ) {
                String place = places[i];
                if( place != null ) {
                    text = stripCommas(place);
                    break;
                }
            }
            // Priority to death event as final place
            if( text != null && i < 4 && places[4] != null ) {
                String place = stripCommas(places[4]);
                if( !place.equals(text) )
                    text += " – " + place;
            } else {
                for( int j = places.length - 1; j > i; j-- ) {
                    String place = places[j];
                    if( place != null ) {
                        place = stripCommas(place);
                        if( !place.equals(text) ) {
                            text += " – " + place;
                            break;
                        }
                    }
                }
            }
            return text;
        }
        return null;
    }

    // riceve un luogo stile Gedcom e restituisce il primo nome tra le virgole
    private static String stripCommas(String place) {
        // salta le virgole iniziali per luoghi tipo ',,,England'
        int start = 0;
        for( char c : place.toCharArray() ) {
            if( c != ',' && c != ' ' )
                break;
            start++;
        }
        place = place.substring(start);
        if( place.indexOf(",") > 0 )
            place = place.substring(0, place.indexOf(","));
        return place;
    }

    /** Count how many near relatives a person has: parents, siblings, step-siblings, spouses and children.
     * Save also the result in the 'kin' extension.
     * @param person The person to start from
     * @return Number of near relatives (person excluded)
     */
    static int countRelatives(Person person) {
        int count = 0;
        if( person != null ) {
            // Famiglie di origine: genitori e fratelli
            List<Family> listaFamiglie = person.getParentFamilies(gc);
            for( Family famiglia : listaFamiglie ) {
                count += famiglia.getHusbandRefs().size();
                count += famiglia.getWifeRefs().size();
                for( Person fratello : famiglia.getChildren(gc) ) // solo i figli degli stessi due genitori, non i fratellastri
                    if( !fratello.equals(person) )
                        count++;
            }
            // Fratellastri e sorellastre
            for( Family famiglia : person.getParentFamilies(gc) ) {
                for( Person padre : famiglia.getHusbands(gc) ) {
                    List<Family> famigliePadre = padre.getSpouseFamilies(gc);
                    famigliePadre.removeAll(listaFamiglie);
                    for( Family fam : famigliePadre )
                        count += fam.getChildRefs().size();
                }
                for( Person madre : famiglia.getWives(gc) ) {
                    List<Family> famiglieMadre = madre.getSpouseFamilies(gc);
                    famiglieMadre.removeAll(listaFamiglie);
                    for( Family fam : famiglieMadre )
                        count += fam.getChildRefs().size();
                }
            }
            // Coniugi e figli
            for( Family famiglia : person.getSpouseFamilies(gc) ) {
                count += famiglia.getWifeRefs().size();
                count += famiglia.getHusbandRefs().size();
                count--; // Minus their self
                count += famiglia.getChildRefs().size();
            }
            // to avoid conflict in github --> don't save this field
            // person.putExtension("kin", count);
        }
        return count;
    }

    @Override
    public void onCreateOptionsMenu( Menu menu, MenuInflater inflater ) {

        SubMenu subMenu = menu.addSubMenu(R.string.order_by);
        subMenu.add(0, 1, 0, R.string.id);
        subMenu.add(0, 2, 0, R.string.surname);
        subMenu.add(0, 3, 0, R.string.date);
        subMenu.add(0, 4, 0, R.string.age);
        subMenu.add(0, 5, 0, R.string.number_relatives);

        // Ricerca nell'Anagrafe
        inflater.inflate( R.menu.cerca, menu );	// già questo basta a far comparire la lente con il campo di ricerca
        final SearchView vistaCerca = (SearchView) menu.findItem(R.id.ricerca).getActionView();
        vistaCerca.setOnQueryTextListener( new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextChange( String query ) {
                adapter.getFilter().filter(query);
                return true;
            }
            @Override
            public boolean onQueryTextSubmit( String q ) {
                vistaCerca.clearFocus();
                return false;
            }
        });
    }
    @Override
    public boolean onOptionsItemSelected( MenuItem item ) {
        int id = item.getItemId();
        if( id > 0 && id <= 5 ) {
            // Clicking twice the same menu item switchs sorting ASC and DESC
            if( order == Order.values()[id * 2 - 1] )
                order = order.next();
            else if( order == Order.values()[id * 2] )
                order = order.prev();
            else
                order = Order.values()[id * 2 - 1];

            if( order == Order.DATE_ASC ) {
                for( Person p : gc.getPeople() ) {
                    int date = findDate(p);
                    if( date < Integer.MAX_VALUE )
                        p.putExtension("date", date);
                    else
                        p.getExtensions().remove("date");
                }
            } else if( order == Order.AGE_ASC ) {
                for( Person p : gc.getPeople() ) {
                    int age = calcAge(p);
                    if( age < Integer.MAX_VALUE )
                        p.putExtension("age", age);
                    else
                        p.getExtensions().remove("age");
                }
            }
            sortPeople();
            adapter.notifyDataSetChanged();
            //U.salvaJson( false ); // dubbio se metterlo per salvare subito il riordino delle persone...
            return true;
        }
        return false;
    }
}
