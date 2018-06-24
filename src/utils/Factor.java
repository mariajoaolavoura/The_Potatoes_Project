package utils;

/**
 * 
 * <b>Factor</b><p>
 * Each instance of this class contains a Double representing the convertion factor between two 
 * types and a flag indicating whether that factor represents the convertion from Type a to Type b
 * (Parent -> Child) or from Type b to Type a (Child -> Parent)
 * @author Ines Justo (84804), Luis Pedro Moura (83808), Maria Joao Lavoura (84681), Pedro Teixeira (84715)
 * @version May-June 2018
 */
public class Factor {

	// Instance Fields
	private final Double factor;
	private final Boolean isChildToParent;

	// CTORs
	/**
	 * Constructor
	 * @param boolean {@code true} it's a factor from Child -> Parent, else {@code false}
	 * @param factor 
	 */
	public Factor(Double factor, Boolean isChildToParent) {
		if (isChildToParent) this.factor = 1 / factor;
		else this.factor = factor;
		this.isChildToParent = isChildToParent;
	}

	// Getters
	/**
	 * 
	 * @return factor
	 */
	public Double getFactor() {
		return factor;
	}

	/**
	 * 
	 * @return {@code true} if factor is for Child -> Parent, else {@code false}
	 */
	public Boolean getIsChildToParent() {
		return isChildToParent;
	}

	// Other Methods
	@Override
	public String toString() {
		StringBuilder builder = new StringBuilder();
		builder.append("Factor [");
		if (factor != null) {
			builder.append("factor=");
			builder.append(factor);
			builder.append(", ");
		}
		if (isChildToParent != null) {
			builder.append("isChildToParent=");
			builder.append(isChildToParent);
		}
		builder.append("]");
		return builder.toString();
	}




}