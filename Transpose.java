
import java.util.*;
import java.io.*;

public class Transpose {

	public static void main(String[] args) throws IOException {

		String inFile = "nobi.dat";
		String outFile = "nobi.t.dat";
	
		String sep_in = " ";
		String sep_out = ", ";
		List<List<String>> m = new ArrayList<List<String>>();
		BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(new File(inFile)), "UTF-8"));
		String line;
		while((line = br.readLine()) != null) {
			String[] ar = line.trim().split(sep_in);
			List<String> l = new ArrayList<String>();
			for(int i=0; i<ar.length; i++)
				l.add(ar[i]);
			m.add(l);
		}
		br.close();

		List<List<String>> t = transpose(m);
		BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(new File(outFile)), "UTF-8"));
		for(int i=0; i<t.size(); i++) {
			for(int j=0; j<t.get(i).size(); j++) {
				if(j>0) bw.write(sep_out);
				bw.write(get(t, i, j));
			}
			bw.write("\n");
		}
		bw.close();
		
	}

	public static <T> T get(List<List<T>> m, int i, int j) {
		return m.get(i).get(j);
	}

	public static <T> List<List<T>> transpose(List<List<T>> m) {
		List<List<T>> o = new ArrayList<List<T>>();

		// take the first row of m and create all the lists in o
		for(int j=0; j<m.get(0).size(); j++) {
			List<T> l = new ArrayList<T>();
			l.add(get(m, 0, j));
			o.add(l);
		}

		// row by row in m, append values to o
		for(int i=1; i<m.size(); i++) {
			for(int j=0; j<m.get(i).size(); j++) {
				o.get(j).add(get(m, i, j));
			}
		}
		return o;
	}

}

