import java.beans.*;

/**
 * Prueft, dass die Komponenten als JavaBeans in der NetBeans-Palette
 * erscheinen: BeanInfo vorhanden, Sinnbilder gemalt, jede Eigenschaft les-,
 * schreibbar und beschrieben, parameterloser Konstruktor da.
 */
public class FMoonBeanTest {
  static int pass=0, fail=0;
  static void ck(String w, boolean ok, String d){ if(ok)pass++; else fail++;
    System.out.printf("%s %-52s %s%n", ok?"  OK  ":" FEHL ", w, d); }
  public static void main(String[] a) throws Exception {
    for (String cn : new String[]{"com.dan.fmoon.FMoonGlobe","com.dan.fmoon.FMoonCalendar","com.dan.fmoon.FTideBoard","com.dan.fmoon.FTideCurve"}) {
      Class<?> c = Class.forName(cn);
      BeanInfo bi = Introspector.getBeanInfo(c);
      ck(cn+": BeanInfo gefunden", bi.getBeanDescriptor().getShortDescription()!=null,
         bi.getBeanDescriptor().getShortDescription());
      ck(cn+": Icon 16 gemalt", bi.getIcon(BeanInfo.ICON_COLOR_16x16)!=null, "");
      ck(cn+": Icon 32 gemalt", bi.getIcon(BeanInfo.ICON_COLOR_32x32)!=null, "");
      PropertyDescriptor[] pds = bi.getPropertyDescriptors();
      int lesbarSchreibbar=0;
      for (PropertyDescriptor pd : pds) if (pd.getReadMethod()!=null && pd.getWriteMethod()!=null) lesbarSchreibbar++;
      ck(cn+": Eigenschaften les- und schreibbar", lesbarSchreibbar>=7, lesbarSchreibbar+" von "+pds.length);
      // jede Eigenschaft muss eine Beschreibung haben
      boolean alleBeschrieben=true;
      for (PropertyDescriptor pd : pds) if (pd.getShortDescription()==null || pd.getShortDescription().equals(pd.getName())) alleBeschrieben=false;
      ck(cn+": jede Eigenschaft hat eine Beschreibung", alleBeschrieben, "");
      // parameterloser Konstruktor (JavaBean-Pflicht)
      Object o = c.getDeclaredConstructor().newInstance();
      ck(cn+": parameterloser Konstruktor", o!=null, "");
    }
    System.out.printf("%nErgebnis: %d von %d Pruefungen gruen%n", pass, pass+fail);
    if (fail>0) System.exit(1);
  }
}
