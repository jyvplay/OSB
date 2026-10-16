package dev.fusion.ooxml
import java.io.StringReader
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import javax.xml.parsers.SAXParserFactory
import org.xml.sax.*
import org.xml.sax.helpers.DefaultHandler

/** Narrow UTF8 XML substrate, not a general validating XML engine. Never resolves external resources.
 * `<!` is refused before SAX: also refuses comments/CDATA deliberately. This is a compatibility restriction.
 * [U] verify SAX behavior on API34/36/37; do not infer Android parser behavior from desktop JDK alone.
 */
object BoundedXml {
    data class Name(val ns:String,val local:String)
    class Node(val name:Name,val attributes:Map<Name,String>) {
        internal val mutableChildren=ArrayList<Node>();internal val content=StringBuilder()
        val children:List<Node> get()=java.util.Collections.unmodifiableList(mutableChildren)
        val text:String get()=content.toString()
        fun attr(local:String,ns:String="")=attributes[Name(ns,local)]
    }
    fun parse(bytes:ByteArray):Node {
        require(bytes.size<=1024*1024)
        val text=Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString().removePrefix("\uFEFF")
        require(!text.contains("<!")) {"Declarations/comments/CDATA not admitted"}
        if(text.startsWith("<?xml")) {
            val end=text.indexOf("?>");require(end in 0..256)
            val header=text.substring(0,end)
            require(!Regex("version\\s*=\\s*['\"](?!1\\.0['\"])").containsMatchIn(header))
            val encoding=Regex("encoding\\s*=\\s*['\"]([^'\"]+)['\"]").find(header)?.groupValues?.get(1)
            require(encoding==null || encoding.equals("UTF-8",true))
        }
        val stack=ArrayList<Node>();var root:Node?=null;var nodes=0;var chars=0;var mappings=0
        val handler=object:DefaultHandler() {
            override fun resolveEntity(publicId:String?,systemId:String?):InputSource {throw SAXException("External resources refused")}
            override fun processingInstruction(target:String?,data:String?) {throw SAXException("Processing instructions refused")}
            override fun error(e:SAXParseException) {throw e}
            override fun fatalError(e:SAXParseException) {throw e}
            override fun startPrefixMapping(prefix:String,uri:String) {require(++mappings<=16 && prefix.length<=128 && uri.length<=2048)}
            override fun startElement(uri:String,local:String,qName:String,attrs:Attributes) {
                require(stack.size<32 && ++nodes<=20000 && attrs.length<=16 && local.length in 1..128 && qName.length<=256 && uri.length<=2048)
                val values=LinkedHashMap<Name,String>()
                for(i in 0 until attrs.length) {
                    require(attrs.getLocalName(i).length in 1..128 && attrs.getURI(i).length<=2048 && attrs.getValue(i).length<=4096)
                    require(values.put(Name(attrs.getURI(i),attrs.getLocalName(i)),attrs.getValue(i))==null)
                }
                val n=Node(Name(uri,local),java.util.Collections.unmodifiableMap(values));mappings=0
                if(stack.isEmpty()) {require(root==null);root=n} else stack.last().mutableChildren.add(n)
                stack.add(n)
            }
            override fun characters(ch:CharArray,start:Int,length:Int) {chars+=length;require(chars<=1024*1024);if(stack.isNotEmpty()) stack.last().content.append(ch,start,length)}
            override fun endElement(uri:String,local:String,qName:String) {require(stack.isNotEmpty());stack.removeAt(stack.lastIndex)}
        }
        val factory=SAXParserFactory.newInstance().apply {isNamespaceAware=true;isValidating=false}
        val reader=factory.newSAXParser().xmlReader
        reader.entityResolver=handler;reader.contentHandler=handler;reader.errorHandler=handler
        reader.parse(InputSource(StringReader(text)))
        require(stack.isEmpty());return requireNotNull(root)
    }
}
