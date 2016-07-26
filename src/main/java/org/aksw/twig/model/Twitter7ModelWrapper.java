package org.aksw.twig.model;

import org.aksw.twig.files.FileHandler;
import org.apache.commons.codec.binary.Hex;
import org.apache.jena.datatypes.xsd.XSDDatatype;
import org.apache.jena.rdf.model.*;
import org.apache.jena.shared.PrefixMapping;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.Writer;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collection;
import java.util.Random;

/**
 * Wraps a {@link Model} using TWIG ontology to create RDF-graphs.
 */
public class Twitter7ModelWrapper {

    private static final Logger LOGGER = LogManager.getLogger(Twitter7ModelWrapper.class);

    public static final String LANG = "Turtle";

    public static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    // Prefix mappings
    private static final String FOAF_IRI = "http://xmlns.com/foaf/0.1/";
    private static final String FOAF_PREF = "foaf";
    private static final String TWIG_IRI = "http://aksw.org/twig#";
    private static final String TWIG_PREF = "twig";
    private static final String OWL_IRI = "http://www.w3.org/2002/07/owl#";
    private static final String OWL_PREF = "owl";
    private static final String RDF_IRI = "http://www.w3.org/1999/02/22-rdf-syntax-ns#";
    private static final String RDF_PREF = "rdf";

    private static final PrefixMapping PREFIX_MAPPING = PrefixMapping.Factory.create();
    static
    {
        PREFIX_MAPPING.setNsPrefix(FOAF_PREF, FOAF_IRI);
        PREFIX_MAPPING.setNsPrefix(TWIG_PREF, TWIG_IRI);
        PREFIX_MAPPING.setNsPrefix(OWL_PREF, OWL_IRI);
        PREFIX_MAPPING.setNsPrefix(RDF_PREF, RDF_IRI);
    }

    // RDF statement parts.
    private static final Resource TWEET = ResourceFactory.createResource(PREFIX_MAPPING.expandPrefix("twig:Tweet"));
    private static final Resource ONLINE_TWITTER_ACCOUNT = ResourceFactory.createResource(PREFIX_MAPPING.expandPrefix("twig:OnlineTwitterAccount"));
    private static final Resource OWL_NAMED_INDIVIDUAL = ResourceFactory.createResource(PREFIX_MAPPING.expandPrefix("owl:NamedIndividual"));
    private static final Property SENDS = ResourceFactory.createProperty(PREFIX_MAPPING.expandPrefix("twig:sends"));
    private static final Property MENTIONS = ResourceFactory.createProperty(PREFIX_MAPPING.expandPrefix("twig:mentions"));
    private static final Property TWEET_TIME = ResourceFactory.createProperty(PREFIX_MAPPING.expandPrefix("twig:tweetTime"));
    private static final Property TWEET_CONTENT = ResourceFactory.createProperty(PREFIX_MAPPING.expandPrefix("twig:tweetContent"));
    private static final Property RDF_TYPE = ResourceFactory.createProperty(PREFIX_MAPPING.expandPrefix("rdf:type"));

    private static byte[] randomHashSuffix = new byte[32];
    static
    {
        new Random().nextBytes(randomHashSuffix);
    }

    private MessageDigest MD5;

    /** The wrapped model. */
    private Model model = ModelFactory.createDefaultModel();

    public Model getModel() {
        return model;
    }

    /**
     * Creates a new instance along with a new {@link Model} to wrap.
     */
    public Twitter7ModelWrapper() {
        this.model.setNsPrefixes(PREFIX_MAPPING);
        try {
            MD5 = MessageDigest.getInstance("MD5");
        } catch (NoSuchAlgorithmException e) {
            throw new ExceptionInInitializerError();
        }
    }

    /**
     * Adds a tweet to the wrapped {@link Model}.
     * @param accountName Name of the tweeting account.
     * @param tweetContent Content of the tweet.
     * @param tweetTime Time of the tweet.
     * @param mentions All mentioned account names of the tweet.
     */
    public void addTweet(String accountName, String tweetContent, LocalDateTime tweetTime, Collection<String> mentions) {
        Resource twitterAccount = getTwitterAccount(accountName);

        String anonymizedTweetContent = tweetContent;
        for (String mention: mentions) {
            anonymizedTweetContent = anonymizedTweetContent.replaceAll(mention, anonymizeTwitterAccount(mention));
        }

        Resource tweet = this.model.getResource(createTweetIri(accountName, tweetTime))
                .addProperty(RDF_TYPE, OWL_NAMED_INDIVIDUAL)
                .addProperty(RDF_TYPE, TWEET)
                .addLiteral(TWEET_CONTENT, model.createTypedLiteral(anonymizedTweetContent))
                .addLiteral(TWEET_TIME, model.createTypedLiteral(tweetTime.format(DATE_TIME_FORMATTER), XSDDatatype.XSDdateTime)); // TODO: add timezone

        twitterAccount.addProperty(SENDS, tweet);

        mentions.forEach(mention -> tweet.addProperty(MENTIONS, getTwitterAccount(mention)));
    }

    /**
     * Gets/creates a resource to a twitter account.
     * @param accountName Name of the twitter account.
     * @return Resource of the twitter account.
     */
    private Resource getTwitterAccount(String accountName) {
        return this.model.getResource(createTwitterAccountIri(accountName))
                .addProperty(RDF_TYPE, OWL_NAMED_INDIVIDUAL)
                .addProperty(RDF_TYPE, ONLINE_TWITTER_ACCOUNT);
    }

    /**
     * Replaces a twitter account with a unique but non deterministic twitterUser_X.
     * @param twitterAccountName User account name.
     * @return Anonymized name.
     */
    private String anonymizeTwitterAccount(String twitterAccountName) {
        MD5.update(twitterAccountName.getBytes());
        MD5.update(randomHashSuffix);
        byte[] hash;
        try {
            hash = MD5.digest();
        } catch (RuntimeException e) {
            LOGGER.error("Exception during anonymizing {}", twitterAccountName);
            return null;
        }
        return Hex.encodeHexString(hash);
    }

    /**
     * Creates the IRI of a twitter account.
     * @param twitterAccountName Name of the account.
     * @return IRI of the twitter account.
     */
    private String createTwitterAccountIri(String twitterAccountName) {
        return prefixedIri(anonymizeTwitterAccount(twitterAccountName));
    }

    /**
     * Creates the IRI of a tweet.
     * @param twitterAccountName Name of the tweeting account.
     * @param messageTime Date and time of the tweet.
     * @return IRI of the tweet.
     */
    private String createTweetIri(String twitterAccountName, LocalDateTime messageTime) {
        String returnValue = anonymizeTwitterAccount(twitterAccountName)
                .concat("_")
                .concat(messageTime.toString().replaceAll(":", "-"));
        return prefixedIri(returnValue);
    }

    /**
     * Prefixes a string with the 'twig:' prefix.
     * @param original String to prefix.
     * @return Prefixed string.
     */
    private static String prefixedIri(String original) {
        return TWIG_IRI.concat(original);
    }

    /**
     * Writes the model into the given writer and deletes the current one.
     * <b>No</b> other methods (such as {@link Writer#flush()}) are invoked at the writer.
     * @param writer Writer to write in.
     */
    public void write(Writer writer) {
        model.write(writer, LANG);
        model = ModelFactory.createDefaultModel();
    }

    /**
     * Reads a TWIG rdf model from a file.
     * @param file File to read from.
     * @return Twitter7ModelWrapper
     * @throws IOException IO error.
     */
    public static Twitter7ModelWrapper read(File file) throws IOException {
        Twitter7ModelWrapper wrapper = new Twitter7ModelWrapper();
        try (InputStream inputStream = FileHandler.getDecompressionStreams(file)) {
            wrapper.model.read(inputStream, null, LANG);
            return wrapper;
        }
    }
}