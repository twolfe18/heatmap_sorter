import java.io.*;
import java.util.*;

// k is the cell type
public class HMArranger {

	static class Permutation<T> implements Iterable<T> {
		private List<T> things;
		private List<Integer> perm;
		
		public Permutation() {
			things = new ArrayList<T>();
			perm = new ArrayList<Integer>();
		}
		
		public Permutation(List<? extends T> items) {
			things = new ArrayList<T>(items);
			perm = new ArrayList<Integer>();
			for(int i=0; i<things.size(); i++)
				perm.add(i);
		}
		
		public Iterator<T> iterator() {
			class PIterator<Q> implements Iterator<Q> {
				private List<Q> myThings;
				private Iterator<Integer> idxIter;
				public PIterator(List<Q> myThings, Iterator<Integer> idxIter) {
					this.myThings = myThings;
					this.idxIter = idxIter;
				}
				@Override
				public boolean hasNext() {
					return idxIter.hasNext();
				}
				@Override
				public Q next() {
					int i = idxIter.next();
					return myThings.get(i);
				}
				@Override
				public void remove() {
					throw new UnsupportedOperationException();
				}
			}
			return new PIterator(things, perm.iterator());
		}
		
		public void shuffle() {
			Collections.shuffle(perm);
		}
		
		public T get(int i) {
			int j = perm.get(i);
			return things.get(j);
		}
		
		public void add(T t) {
			things.add(t);
			perm.add(perm.size());
		}
		
		public Permutation<T> copy() {
			Permutation<T> p = new Permutation();
			p.things = things;
			p.perm = new ArrayList<Integer>(perm);
			return p;
		}
		
		public Permutation<T> mutate(int N, Random r) {
			// choose N things, shuffle those indices
			Set<Integer> seen_s = new HashSet<Integer>();
			List<Integer> seen_l = new ArrayList<Integer>();
			while(seen_s.size() < N) {
				int i = r.nextInt(perm.size());
				if(seen_s.add(i)) seen_l.add(i);
			}
			Collections.shuffle(seen_l, r);
//			System.out.println("[mutate] seen_l = " + seen_l);
			Permutation<T> p = copy();
//			System.out.println("[mutate] before p.perm = " + p.perm);
			p.shift(seen_l);
//			System.out.println("[mutate] after p.perm = " + p.perm);
			return p;
		}
		
		/**
		 * generalization of swap
		 * shift([i,j]) =>   swap(i,j)
		 * shift([i,j,k]) => perm[i,j,k] = perm[j,k,i]
		 * ...
		 */
		private void shift(List<Integer> idx) {
			int leftMost = perm.get(idx.get(0));
			for(int i=0; i<idx.size()-1; i++) {
				int left = idx.get(i);
				int right = idx.get(i+1);
				perm.set(left, perm.get(right));
			}
			perm.set(idx.get(idx.size()-1), leftMost);
		}
		
		public int size() {
			assert things.size() == perm.size();
			return things.size();
		}
	}
	
	static interface Row<T> {
		public String getName();
		public T get(int i);
		public List<T> getCols();
		public int numCols();
		public double disagreement(Row<T> other);	// must be >= 0
	}
	
	static class DoubleRow implements Row<Double> {
		private String name;
		private List<Double> elems;
		public DoubleRow(String name, List<Double> elems) {
			this.name = new String(name);
			this.elems = new ArrayList<Double>(elems);
		}
		public String getName() { return name; }
		public Double get(int i) { return elems.get(i); }
		public List<Double> getCols() { return elems; }
		public int numCols() { return elems.size(); }
		public double disagreement(Row<Double> other) {
			assert numCols() == other.numCols();
			double ds = 0.0;
			for(int i=0; i<numCols(); i++) {
				double d = get(i) - other.get(i);
				ds += d*d;
			}
			return ds;
		}
	}
	
	private Permutation<DoubleRow> rows;
	private List<String> colNames;
	private double greedy = 1.0;
	private int effort = 100;
	private int change_size = 2;
	private Random rand = new Random();
	
	private Permutation<DoubleRow> best_rows;
	private double best_score = -1.0/0.0;
	
	public HMArranger(List<String> colNames) {
		this.colNames = new ArrayList(colNames);
		rows = new Permutation<DoubleRow>();
		best_rows = rows;
	}
	
	public HMArranger(List<String> colNames, int change_size) {
		this(colNames);
		this.change_size = change_size;
	}
	
	public List<String> getColNames() { return colNames; }
	
	public void setRandom(Random r) { rand = r; }
	public void setGreedy(double g) { greedy = g; }
	public int numRows() { return rows.size(); }
	
	@Override
	public String toString() {
		StringBuffer sb = new StringBuffer();
		sb.append(String.format("greedy = %.1f, effort = %d, " +
				"change size = %d\n", greedy, effort, change_size));
		sb.append("titles = " + colNames + "\n");
		for(DoubleRow r : rows)
			sb.append(r.getName() + " -> " + r.getCols() + "\n");
		return sb.toString();
	}
	
	public void addRow(String rowname, List<Double> elements) {
		DoubleRow r = new DoubleRow(rowname, elements);
		rows.add(r);
	}
	
	private static <S> double score(Permutation<? extends Row<S>> rows) {
		double penalty = 0;
		for(int i=0; i<rows.size()-1; i++) {
			Row<S> r1 = rows.get(i);
			Row<S> r2 = rows.get(i+1);
			// 1-neighbors
			penalty += r1.disagreement(r2);
			// 2-neighbors
			if(i < rows.size()-2) {
				Row<S> r3 = rows.get(i+2);
				penalty += r1.disagreement(r3);
			}
		}
		return -penalty;
	}
	
	public boolean optimize(int maxIter) {
		int i=0;
		while(i++ < maxIter && improve());
		rows = best_rows;
		best_rows = best_rows.copy();
		return i < maxIter;
	}
	
	public void shuffle() { rows.shuffle(); }
	
	public boolean improve() {
		// p(accept) = logistic(greedy*(score(conf1) - score(conf2)))
		double old_score = score(rows);
		for(int i=0; i<effort; i++) {
			Permutation<DoubleRow> mutation = rows.mutate(change_size, rand);
			double new_score = score(mutation);
			double d = greedy * (new_score - old_score);
			double accept = 1.0/(1.0+Math.exp(-d));
//			System.out.printf("[improve] i=%d, old_score = %.1f, new_score = %.1f, " +
//					"greedy = %.1f, d = %.1f, accept = %.1f\n",
//					i, old_score, new_score, greedy, d, accept);
			if(rand.nextDouble() < accept) {
				rows = mutation;
				updateBest(rows);
				return true;
			}
		}
		updateBest(rows);
		return false;
	}
	
	private void updateBest(Permutation<DoubleRow> p) {
		double s = score(p);
		if(s > best_score) {
			best_score = s;
			best_rows = p;
		}
	}
	
	public double getBestScore() { return best_score; }
	
	static class HMThread implements Runnable {
		public HMArranger hma;
		public int maxIter;
		public HMThread(HMArranger hma, int maxIter) {
			this.hma = hma;
			this.maxIter = maxIter;
		}
		@Override
		public void run() {
			hma.shuffle();
			hma.optimize(maxIter);
		}
	}
	
	public static void writeout(HMArranger hma, File csv) throws IOException {
		BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(csv), "UTF-8"));
		bw.write(csvLine("rowName", hma.getColNames())+"\n");
		for(DoubleRow r : hma.best_rows)
			bw.write(csvLine(r.name, r.elems) + "\n");
		bw.close();
	}
	
	public static String csvLine(String leftMost, List<?> right) {
		StringBuffer sb = new StringBuffer();
		sb.append(leftMost);
		for(Object o : right)
			sb.append(", " + o);
		return sb.toString();
	}
	
	public static String csvLine(List<?> toks) {
		return csvLine(toks.get(0)+"", toks.subList(1, toks.size()));
	}
	

	public static void main(String[] args) throws Exception {
		
		final int maxIter = 10;						// runs up to (maxIter*k*log(k)) iterations for a length k csv
		final int N = 8;							// how many threads / parallel searches
		final int[] changeSizes = new int[]{2, 3, 4, 5};	// how many indices per mutation
		final double greedy = 1.0;					// 0 = random search, infinity = hillclimbing
		
		if(args.length != 1) {
			System.out.println("please provide one csv filename");
			return;
		}
		
		File csv = new File("/home/travis/Dropbox/code/heatmap/test.csv");
		BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(csv), "UTF-8"));
		
		Thread[] threads = new Thread[N];
		HMThread[] hmts = new HMThread[N];
		
		// read the header
		String line= br.readLine();
		String[] ar = line.split(",\\s*");
		List<String> titles = Arrays.asList(ar).subList(1, ar.length);
		for(int i=0; i<N; i++) {
			int change_size = changeSizes[i % changeSizes.length];
			HMArranger hma = new HMArranger(titles, change_size);
			hma.setRandom(new Random(i*3571));
			hmts[i] = new HMThread(hma, 100);
			threads[i] = new Thread(hmts[i]);
		}
		
		// read remaining rows
		while((line = br.readLine()) != null) {
			line = line.trim();
			ar = line.split(",\\s*");
			String title = ar[0];
			List<Double> elements = new ArrayList<Double>();
			for(int i=1; i<ar.length; i++)
				elements.add(Double.parseDouble(ar[i]));
			for(int i=0; i<N; i++)
				hmts[i].hma.addRow(title, elements);
		}
		br.close();
		
		// calcuate good permutation
		for(int i=0; i<N; i++) {
			double k = hmts[i].hma.numRows();
			hmts[i].maxIter = (int)(maxIter * k * Math.log(k));
			threads[i].start();
		}
		for(int i=0; i<N; i++) threads[i].join();
		
		double best_score = -999999;
		HMArranger best = null; 
		for(int i=0; i<N; i++) {
			double s = hmts[i].hma.getBestScore();
//			System.out.println(i);
//			System.out.println(s);
//			System.out.println(hmts[i].hma);
			if(s > best_score) {
				best_score = s;
				best = hmts[i].hma;
			}
		}
		
		// writeout best permutation
		writeout(best, new File(csv.getCanonicalPath() + ".better"));
	}
}

