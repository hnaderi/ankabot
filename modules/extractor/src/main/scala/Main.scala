// package io.aibees.knowledgebase

// import cats.effect.IO
// import cats.effect.IOApp
// import cats.syntax.all.*
// import fs2.io.file.Files
// import fs2.io.file.Path
// import io.circe.syntax.*

// import java.nio.file.{Files => JFiles}
// import java.nio.file.{Path => JPath}

// object Main extends IOApp.Simple {
//   override def run: IO[Unit] = Wappalyzer(
//     Path("/storage/projects/ai-bees/knowledge-base/patterns")
//   )
//     // .map(_.values.filter(_.patterns != TechnologyPatterns.empty).size)
//     .flatMap(all =>
//       // val supported = all.values.filter(_.patterns != TechnologyPatterns.empty)

//       val meta = Vector(
//         "description",
//         "website",
//         "cats",
//         "icon",
//         "cpe",
//         "saas",
//         "oss",
//         "pricing",
//         "implies",
//         "requires",
//         "requiresCategory",
//         "excludes"
//       )
//       val notSupported = all.view
//         .filter { (key, p) =>
//           import p.patterns
//           patterns.cookies.isEmpty && patterns.headers.isEmpty && patterns.url.isEmpty && patterns.meta.isEmpty && patterns.scriptSrc.isEmpty
//         }
//         .mapValues { p =>
//           p.patterns.json.asObject.toVector
//             .flatMap(_.keys)
//             .filterNot(meta.contains)
//         }
//         .toVector

//       val stats =
//         notSupported
//           .flatMap(_._2)
//           .foldLeft(Map.empty[String, Int]) { (m, v) =>
//             m.updatedWith(v) {
//               case None        => Some(1)
//               case Some(value) => Some(value + 1)
//             }
//           }
//           .map((k, v) => s"$k, $v")
//           .mkString("\n")

//       val onlyJS =
//         notSupported.filter(_._2 == Vector("js")).size

//       val onlyDNS =
//         notSupported.filter(_._2 == Vector("js")).map(_._1)

//       IO.println(onlyDNS)
//       // notSupported.traverse((name, keys) => IO.println(s"$name -> $keys")) *>
//       //   IO.println(s"\nOverview:\n$stats") >>
//       //   IO.println(s"Only js: $onlyJS")
//     )

//   // .map(_.asJson.noSpaces)
//   // .flatMap(out =>
//   //   IO.blocking(
//   //     JFiles.writeString(JPath.of("src/main/resources/patterns.json"), out)
//   //   )
//   // )
// //     .flatMap { technologies =>

// //       val filtered = technologies
// //         .filterNot((k, v) => v.patterns == TechnologyPatterns.empty)

// //       IO.println(s"""
// // All: ${technologies.size}
// // Filtered: ${filtered.size}
// // """)
// //         // .traverse((key, value) =>
// //         //   IO.println(s"$key -> ${value.patterns.scriptSrc}")
// //         // )
// //         .void
// //     }
// }
