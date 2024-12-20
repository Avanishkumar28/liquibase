package liquibase.command;

import liquibase.Scope;
import liquibase.configuration.ConfigurationValueConverter;
import liquibase.configuration.ConfigurationValueObfuscator;
import liquibase.exception.CommandValidationException;
import liquibase.exception.MissingRequiredArgumentException;
import liquibase.integration.commandline.LiquibaseCommandLineConfiguration;
import liquibase.util.ObjectUtil;
import liquibase.util.StringUtil;
import lombok.Getter;
import lombok.Setter;

import java.util.*;
import java.util.regex.Pattern;

/**
 * Defines a known, type-safe argument for a specific {@link CommandStep}.
 * Includes metadata about the argument such as a description, if it is required, a default value, etc.
 * <p>
 * Because this definition is tied to a specific step, multiple steps in a pipeline can define arguments of the same name.
 *
 * @see CommandBuilder#argument(String, Class) for constructing new instances.
 */
public class CommandArgumentDefinition<DataType> implements Comparable<CommandArgumentDefinition<?>> {

    private static final String ALLOWED_ARGUMENT_REGEX = "[a-zA-Z0-9]+";
    private static final Pattern ALLOWED_ARGUMENT_PATTERN = Pattern.compile(ALLOWED_ARGUMENT_REGEX);

    /**
     * -- GETTER --
     *  The name of the argument. Must be camelCase alphanumeric.
     */
    @Getter
    private final String name;
    private final SortedSet<String> aliases = new TreeSet<>();
    private final Set<String> forcePrintedAliases = new HashSet<>();
    /**
     * -- GETTER --
     *  The datatype this argument will return.
     */
    @Getter
    private final Class<DataType> dataType;
    /**
     * -- GETTER --
     *  The description of the argument. Used in generated help documentation.
     */
    @Getter
    private String description;
    /**
     * -- GETTER --
     * Whether this argument is required. Exposed as a separate setting for help doc purposes.
     * {@link #validate(CommandScope)} will ensure required values are set.
     */
    @Getter
    private boolean required;
    private boolean hidden;
    /**
     * -- GETTER --
     *  The default value to use for this argument
     */
    @Getter
    private DataType defaultValue;
    /**
     * -- GETTER --
     * A description of the default value. Defaults to {@link String#valueOf(Object)} of {@link #getDefaultValue()} but
     * can be explicitly with {@link Building#defaultValue(Object, String)}.
     */
    @Getter
    private String defaultValueDescription;
    /**
     * -- GETTER --
     * Function for converting values set in underlying {@link liquibase.configuration.ConfigurationValueProvider}s into the
     * type needed for this command.
     */
    @Getter
    private ConfigurationValueConverter<DataType> valueConverter;
    /**
     * -- GETTER --
     *  Used when sending the value to user output to protect secure values.
     */
    @Getter
    private ConfigurationValueObfuscator<DataType> valueObfuscator;
    @Setter
    @Getter
    private CommandArgumentDefinition<?> supersededBy;

    protected CommandArgumentDefinition(String name, Class<DataType> type) {
        this.name = name;
        this.dataType = type;
        this.valueConverter = value -> ObjectUtil.convert(value, type, name);
    }

    /**
     * Aliases for the argument.  Must be camelCase alphanumeric.
     */
    public SortedSet<String> getAliases() {
        return Collections.unmodifiableSortedSet(aliases);
    }

    public Set<String> getForcePrintedAliases() {
        return Collections.unmodifiableSet(forcePrintedAliases);
    }

    /**
     * Hidden arguments are ones that can be called via integrations, but should not be normally shown in help to users.
     */
    public boolean getHidden() {
        return hidden;
    }

    /**
     * Validates that the value stored in the given {@link CommandScope} is valid.
     *
     * @throws CommandValidationException if the stored value is not valid.
     */
    public void validate(CommandScope commandScope) throws CommandValidationException {
        final DataType currentValue = commandScope.getArgumentValue(this);
        if (this.isRequired() && currentValue == null &&
           (this.getSupersededBy()  == null || commandScope.getArgumentValue(this.getSupersededBy()) == null)) {
                throw new CommandValidationException(LiquibaseCommandLineConfiguration.ARGUMENT_CONVERTER.getCurrentValue().convert(this.getName()), "missing required argument", new MissingRequiredArgumentException(this.getName()));
        }
    }

    @Override
    public int compareTo(CommandArgumentDefinition<?> o) {
        return this.getName().compareTo(o.getName());
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        CommandArgumentDefinition that = (CommandArgumentDefinition) o;
        return Objects.equals(name, that.name);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name);
    }

    @Override
    public String toString() {
        String returnString = getName();

        if (required) {
            returnString += " (required)";
        }

        return returnString;
    }

    /**
     * A new {@link CommandArgumentDefinition} under construction from {@link CommandBuilder}
     */
    public static class Building<DataType> {
        private final String[][] commandNames;
        private final CommandArgumentDefinition<DataType> newCommandArgument;

        Building(String[][] commandNames, CommandArgumentDefinition<DataType> newCommandArgument) {
            this.commandNames = commandNames;
            this.newCommandArgument = newCommandArgument;

            //
            // If the argument name is "url", then we set up an obfuscator to avoid bleeding credentials
            // via the logging framework
            //
            if (newCommandArgument.getName().equalsIgnoreCase(CommonArgumentNames.URL.getArgumentName()) ||
                newCommandArgument.getName().equalsIgnoreCase(CommonArgumentNames.REFERENCE_URL.getArgumentName())) {
                this.setValueObfuscator((ConfigurationValueObfuscator<DataType>) ConfigurationValueObfuscator.URL_OBFUSCATOR);
            }
        }

        /**
         * Mark argument as required.
         * @see #optional()
         */
        public Building<DataType> required() {
            this.newCommandArgument.required = true;

            return this;
        }

        /**
         * Specifies a CommandArgument that can replace this one if it is not available.
         *
         */
        public Building<DataType> supersededBy(CommandArgumentDefinition<?> commandArgumentDefinition) {
            this.newCommandArgument.supersededBy = commandArgumentDefinition;

            return this;
        }

        /**
         * Mark argument as optional.
         * @see #required()
         */
        public Building<DataType> optional() {
            this.newCommandArgument.required = false;

            return this;
        }

        /**
         * Mark argument as hidden.
         */
        public Building<DataType> hidden() {
            this.newCommandArgument.hidden = true;

            return this;
        }

        /**
         * Add a description
         */
        public Building<DataType> description(String description) {
            this.newCommandArgument.description = description;

            return this;
        }

        /**
         * Set the default value for this argument as well as the description of the default value.
         */
        public Building<DataType> defaultValue(DataType defaultValue, String description) {
            newCommandArgument.defaultValue = defaultValue;
            newCommandArgument.defaultValueDescription = description;

            return this;
        }

        /**
         * Convenience version of {@link #defaultValue(Object, String)} but using {@link String#valueOf(Object)} for the description.
         */
        public Building<DataType> defaultValue(DataType defaultValue) {
            String description = null;
            if (defaultValue != null) {
                description = String.valueOf(defaultValue);
            }
            return this.defaultValue(defaultValue, description);
        }

        /**
         * Set the {@link #getValueConverter()} to use.
         */
        public Building<DataType> setValueHandler(ConfigurationValueConverter<DataType> valueHandler) {
            newCommandArgument.valueConverter = valueHandler;
            return this;
        }

        /**
         * Set the {@link #getValueObfuscator()} to use.
         */
        public Building<DataType> setValueObfuscator(ConfigurationValueObfuscator<DataType> valueObfuscator) {
            newCommandArgument.valueObfuscator = valueObfuscator;
            return this;
        }


        /**
         * Adds an alias for this command argument; an alias added this way is not shown in the help output.
         */
        public Building<DataType> addAlias(String alias) {
            newCommandArgument.aliases.add(alias);
            return this;
        }

        /**
         * Adds an alias for this command argument that will be printed to the help output inline with the existing
         * parameter (for which this is an alias).
         */
        public Building<DataType> addForcePrintAlias(String alias) {
            addAlias(alias);
            newCommandArgument.forcePrintedAliases.add(alias);
            return this;
        }
        /**
         * Complete construction and register the definition with the rest of the system.
         *
         * @throws IllegalArgumentException is an invalid configuration was specified
         */
        public CommandArgumentDefinition<DataType> build() throws IllegalArgumentException {
            return build(false);
        }

        /**
         * Complete construction and register the definition with the rest of the system.
         *
         * @throws IllegalArgumentException is an invalid configuration was specified
         */
        public CommandArgumentDefinition<DataType> build(boolean allowDuplicates) throws IllegalArgumentException {
            if (!ALLOWED_ARGUMENT_PATTERN.matcher(newCommandArgument.name).matches()) {
                throw new IllegalArgumentException("Invalid argument format: " + newCommandArgument.name);
            }

            for (String[] commandName : commandNames) {
                try {
                    Scope.getCurrentScope().getSingleton(CommandFactory.class).register(commandName, newCommandArgument, allowDuplicates);
                } catch (IllegalArgumentException iae) {
                    Scope.getCurrentScope().getLog(CommandArgumentDefinition.class).warning(
                            "Unable to register command '" + StringUtil.join(commandName, " ") + "' argument '" + newCommandArgument.getName() + "': " + iae.getMessage());
                    throw iae;
                }
            }

            return newCommandArgument;
        }
    }

}
