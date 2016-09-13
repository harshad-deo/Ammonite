package ammonite.kernel

import fastparse.noApi._

import scalaparse.Scala._
import WhitespaceApi._
import Parsed.Failure

private[kernel] object Parsers {

  val Splitter = {
    P(statementBlocl(Fail) ~ WL ~ End)
  }

  // For some reason Scala doesn't import this by default
  private val `_` = scalaparse.Scala.`_`

  val ImportSplitter: P[Seq[ImportTree]] = {
    val IdParser = P((Id | `_`).!).map(
      s => if (s(0) == '`') s.drop(1).dropRight(1) else s
    )
    val Selector = P(IdParser ~ (`=>` ~/ IdParser).?)
    val Selectors = P("{" ~/ Selector.rep(sep = ",".~/) ~ "}")
    val BulkImport = P(`_`).map(
      _ => Seq("_" -> None)
    )
    val Prefix = P(IdParser.rep(1, sep = "."))
    val Suffix = P("." ~/ (BulkImport | Selectors))
    val ImportExpr: P[ImportTree] = {
      // Manually use `WL0` parser here, instead of relying on WhitespaceApi, as
      // we do not want the whitespace to be consumed even if the WL0 parser parses
      // to the end of the input (which is the default behavior for WhitespaceApi)
      P(Index ~~ Prefix ~~ (WL0 ~~ Suffix).? ~~ Index).map {
        case (start, idSeq, selectors, end) => ImportTree(idSeq, selectors, start, end)
      }
    }
    P(`import` ~/ ImportExpr.rep(1, sep = ",".~/))
  }

  private val Prelude = P((Annot ~ OneNLMax).rep ~ (Mod ~/ Pass).rep)

  private val Statement = P(scalaparse.Scala.TopPkgSeq | scalaparse.Scala.Import | Prelude ~ BlockDef | StatCtx.Expr)

  private def statementBlocl(blockSep: P0) = P(Semis.? ~ (!blockSep ~ Statement ~~ WS ~~ (Semis | End)).!.repX)

  /**
    * Attempts to break a code blob into multiple statements. Returns `None` if
    * it thinks the code blob is "incomplete" and requires more input
    */
  def split(code: String): Option[fastparse.core.Parsed[Seq[String]]] =
    Splitter.parse(code) match {
      case Failure(_, index, extra) if code.drop(index).trim() == "" => None
      case x => Some(x)
    }

}
