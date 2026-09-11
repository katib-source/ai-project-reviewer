import { jsPDF } from 'jspdf'
import { API_BASE_URL } from './apiEndpoints'

function getLatexValue(latex: string, command: string) {
  return latex.match(new RegExp(`\\\\${command}\\{([^}]*)\\}`))?.[1]
}

function latexToText(latex: string) {
  return latex
    .replace(/\\documentclass\{[^}]*\}/g, '')
    .replace(/\\title\{[^}]*\}/g, '')
    .replace(/\\date\{[^}]*\}/g, '')
    .replace(/\\begin\{document\}|\\end\{document\}|\\maketitle/g, '')
    .replace(/\\(?:sub)?section\*?\{([^}]*)\}/g, '\n$1\n')
    .replace(/\\%/g, '%')
    .replace(/\\n/g, '\n')
    .replace(/[{}]/g, '')
    .trim()
}

export function downloadPdfFromLatex(latex: string, fileName: string) {
  const pdf = new jsPDF({ unit: 'pt', format: 'a4' })
  const title = getLatexValue(latex, 'title') ?? 'AI Project Reviewer report'
  const lines = pdf.splitTextToSize(latexToText(latex), 475) as string[]

  pdf.setProperties({ title })
  pdf.setFont('times', 'bold')
  pdf.setFontSize(19)
  pdf.text(title, 60, 62)
  pdf.setDrawColor(46, 96, 82)
  pdf.line(60, 76, 535, 76)
  pdf.setFont('helvetica', 'normal')
  pdf.setFontSize(10.5)

  let y = 105
  for (const line of lines) {
    if (y > 780) {
      pdf.addPage()
      y = 60
    }
    pdf.text(line, 60, y)
    y += 16
  }

  pdf.save(`${fileName}.pdf`)
}

export async function downloadRemoteReport(path: string, fileName: string) {
  const response = await fetch(`${API_BASE_URL}${path}`)
  if (!response.ok) {
    throw new Error(`Report download failed with status ${response.status}.`)
  }

  const blob = await response.blob()
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = fileName
  link.click()
  URL.revokeObjectURL(url)
}