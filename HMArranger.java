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
			if(N == 2) return mutate2(r);
			// choose N things, shuffle those indices
			Set<Integer> seen_s = new HashSet<Integer>();
			List<Integer> seen_l = new ArrayList<Integer>();
			while(seen_s.size() < N) {
				int i = r.nextInt(perm.size());
				if(seen_s.add(i)) seen_l.add(i);
			}
//			System.out.println("[mutate] seen_l = " + seen_l);
			Permutation<T> p = copy();
//			System.out.println("[mutate] before p.perm = " + p.perm);
			p.shift(seen_l);
//			System.out.println("[mutate] after p.perm = " + p.perm);
			return p;
		}

		private Permutation<T> mutate2(Random r) {
			final int N = perm.size();
			final int left = r.nextInt(N);
			int right = left;
			while(right == left)
				right = r.nextInt(N);
			Permutation<T> p = copy();
			int temp = p.perm.get(left);
			p.perm.set(left, p.perm.get(right));
			p.perm.set(right, temp);
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
			double nd;
			double ds = 0.0;
			for(int i=0; i<numCols(); i++) {
				double d = get(i) - other.get(i);
				ds += d*d;

				// reward disagreeing on the diagonal
				final double diag_rew_const = 0.1;
				if(i > 0) {
					nd = get(i) - other.get(i-1);
					ds -= diag_rew_const * (nd*nd);
				}
				if(i < numCols()-1) {
					nd = get(i) - other.get(i+1);
					ds -= diag_rew_const * (nd*nd);
				}
			}
			return ds;
		}
	}
	
	private Permutation<DoubleRow> rows;
	private List<String> colNames;
	private double greedy = 1.0;
	private int effort = 100;
	private int change_size = 2;
	private int neighbors = 50;
	private Random rand = new Random();

	private double p_shuffle = 0.0, p_return_to_best = 0.005;
	
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
	
	public String getParamString() {
		return String.format("greedy = %.1f, change_size = %d, p_shuffle = %.1e, p_return_to_best = %.1e, neighbors=%d",
			greedy, change_size, p_shuffle, p_return_to_best, neighbors);
	}
	public List<String> getColNames() { return colNames; }
	
	public void setRandom(Random r) { rand = r; }
	public void setGreedy(double g) { greedy = g; }
	public void setNeighbors(int n) { neighbors = n; }
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
	
	private static <S> double score(Permutation<? extends Row<S>> rows, int neighbors) {
		double c2 = 0.95;
		double penalty = 0;
		for(int i=0; i<rows.size()-1; i++) {
			Row<S> r1 = rows.get(i);
			// neighbors
			double c = 1.0/c2;
			for(int j=1; j<neighbors && i+j<rows.size(); j++) {
				c *= c2;
				Row<S> r2 = rows.get(i+j);
				penalty += c2 * r1.disagreement(r2);
			}
		}
		return -penalty;
	}
	
	private static <S> double score(Permutation<? extends Row<S>> rows, List<Row> changed) {
		return 0.0;
	}
	
	public boolean optimize(int maxIter) {
		int i;
		for(i=0; i<maxIter; i++) {
			double r = rand.nextDouble();
			if(r < p_shuffle)
				rows.shuffle();
			else if(r < p_shuffle + p_return_to_best)
				rows = best_rows.copy();
			else
				improve();

			if(i % 5000 == 0) {
				System.out.printf("[optimize] score = %.2f, best score = %.3f, params = %s\n",
					getScore(), getBestScore(), getParamString());
			}
		}
		rows = best_rows;
		best_rows = best_rows.copy();
		return i < maxIter;
	}
	
	public void shuffle() {
		System.out.println("shuffling, rows.size = " + rows.size());
		rows.shuffle();
	}
	
	public boolean improve() {
		// p(accept) = logistic(greedy*(score(conf1) - score(conf2)))
		double old_score = score(rows, neighbors);
		for(int i=0; i<effort; i++) {
			Permutation<DoubleRow> mutation = rows.mutate(change_size, rand);
			double new_score = score(mutation, neighbors);
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

	public double getScore() {
		return HMArranger.score(rows, neighbors);
	}
	
	private void updateBest(Permutation<DoubleRow> p) {
		double s = score(p, neighbors);
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
			//hma.shuffle();
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
		
		final int maxIter = 20;						// runs up to (maxIter*k*log(k)) iterations for a length k csv
		final int neighbors = 100;
		final int N = 4;							// how many threads / parallel searches
		final int[] changeSizes = new int[]{2};	// how many indices per mutation
		final double greedy = 2.0;					// 0 = random search, infinity = hillclimbing
		
		if(args.length != 1) {
			System.out.println("please provide one csv filename");
			return;
		}
		
		File csv = new File(args[0]);
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
			hma.setGreedy(greedy);
			hma.setNeighbors(neighbors);
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
		
		for(int i=0; i<N; i++) {
			HMArranger hma = hmts[i].hma;
			double s0 = hma.getScore();
			//hma.shuffle();
			double s1 = hma.getScore();
			System.out.printf("in score %.1f, rand score %.1f\n", s0, s1);
		}
		
		// calcuate good permutation
		for(int i=0; i<N; i++) {
			double k = hmts[i].hma.numRows();
			hmts[i].maxIter = (int)(maxIter * k * Math.log(k));
			System.out.printf("starting thread with maxIter = %d\n", hmts[i].maxIter);
			threads[i].start();
		}
		for(int i=0; i<N; i++) threads[i].join();
		
		double best_score = -999999;
		HMArranger best = null; 
		for(int i=0; i<N; i++) {
			double s = hmts[i].hma.getBestScore();
			String p = hmts[i].hma.getParamString();
			System.out.println(i + "(" + p + ") => " + s);
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

