package com.playswitch.ziadzero.root

import java.io.StringReader
import java.io.StringWriter
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.transform.OutputKeys
import javax.xml.transform.TransformerFactory
import javax.xml.transform.dom.DOMSource
import javax.xml.transform.stream.StreamResult
import org.w3c.dom.Element
import org.xml.sax.InputSource

object FinskyXmlHelper {
    private fun newDocumentBuilderFactory(): DocumentBuilderFactory =
        DocumentBuilderFactory.newInstance().apply {
            runCatching { setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) }
            runCatching { setFeature("http://xml.org/sax/features/external-general-entities", false) }
            runCatching { setFeature("http://xml.org/sax/features/external-parameter-entities", false) }
            isExpandEntityReferences = false
        }

    fun readAccount(xmlContent: String): String? {
        if (xmlContent.isBlank()) return null
        val doc = newDocumentBuilderFactory()
            .newDocumentBuilder()
            .parse(InputSource(StringReader(xmlContent)))
        val strings = doc.getElementsByTagName("string")
        for (i in 0 until strings.length) {
            val el = strings.item(i) as? Element ?: continue
            if (el.getAttribute("name") == "account") {
                return el.textContent?.trim()?.takeIf { it.isNotEmpty() }
            }
        }
        return null
    }

    fun updateAccountIfPresent(xmlContent: String, targetEmail: String): String? {
        if (readAccount(xmlContent) == null) return null
        return updateAccount(xmlContent, targetEmail)
    }

    fun updateAccount(xmlContent: String, targetEmail: String): String {
        val factory = newDocumentBuilderFactory()
        val builder = factory.newDocumentBuilder()
        val doc = builder.parse(InputSource(StringReader(xmlContent)))
        val strings = doc.getElementsByTagName("string")
        var updated = false
        for (i in 0 until strings.length) {
            val el = strings.item(i) as? Element ?: continue
            if (el.getAttribute("name") == "account") {
                el.textContent = targetEmail
                updated = true
                break
            }
        }
        if (!updated) {
            val root = doc.documentElement
            val newEl = doc.createElement("string").apply {
                setAttribute("name", "account")
                textContent = targetEmail
            }
            root.appendChild(newEl)
        }

        val writer = StringWriter()
        TransformerFactory.newInstance().newTransformer().apply {
            setOutputProperty(OutputKeys.ENCODING, "UTF-8")
            setOutputProperty(OutputKeys.INDENT, "yes")
            transform(DOMSource(doc), StreamResult(writer))
        }
        return writer.toString()
    }
}
