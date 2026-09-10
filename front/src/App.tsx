import { useEffect, useRef, useState } from 'react'
import { Bell, ChevronDown, Download, FolderUp, Play, ShieldCheck, Sparkles } from 'lucide-react'
import './App.css'
import { FindingsTable } from './components/FindingsTable'
import { MetricCard } from './components/MetricCard'
import { ProjectTree } from './components/ProjectTree'
import { Sidebar, type WorkspaceView } from './components/Sidebar'
import { importProjectArchive } from './services/projectImportService'
import { downloadPdfFromLatex } from './services/reportExportService'
import { reviewService, type Finding, type ReviewProject } from './services/reviewService'

function App() {
  const [activeView, setActiveView] = useState<WorkspaceView>('overview')
  const [project, setProject] = useState<ReviewProject | null>(null)
  const [isRunning, setIsRunning] = useState(false)
  const [selectedFinding, setSelectedFinding] = useState<Finding | null>(null)
  const [reportState, setReportState] = useState<'idle' | 'generating' | 'ready'>('idle')
  const [isImporting, setIsImporting] = useState(false)
  const [isUploading, setIsUploading] = useState(false)
  const [importError, setImportError] = useState<string | null>(null)
  const [selectedArchive, setSelectedArchive] = useState<File | null>(null)
  const [highlightedSection, setHighlightedSection] = useState<string | null>(null)
  const fileInputRef = useRef<HTMLInputElement>(null)

  useEffect(() => {
    void reviewService.getCurrentProject().then(setProject)
  }, [])

  const startAnalysis = async () => {
    setIsRunning(true)
    await reviewService.startAnalysis()
    setIsRunning(false)
    setReportState('ready')
  }

  const exportReport = async () => {
    if (!project) return

    setReportState('generating')
    const report = reviewService.createReport(project)
    downloadPdfFromLatex(report, `${project.name}-review-report`)
    setReportState('ready')
  }

  const navigateTo = (view: WorkspaceView) => {
    setActiveView(view)
    const sectionId = view === 'project' ? 'project-files' : view === 'analysis' ? 'analysis' : view === 'reports' ? 'report' : 'overview'
    document.getElementById(sectionId)?.scrollIntoView({ behavior: 'smooth', block: 'center' })
    setHighlightedSection(sectionId)
    window.setTimeout(() => setHighlightedSection(null), 1400)
  }

  const selectArchive = (event: React.ChangeEvent<HTMLInputElement>) => {
    const file = event.target.files?.[0]
    if (!file) return
    setSelectedArchive(file)
    setImportError(null)
  }

  const importProject = async (event: React.FormEvent<HTMLFormElement>) => {
    event.preventDefault()
    if (!selectedArchive) {
      fileInputRef.current?.click()
      return
    }

    const formData = new FormData(event.currentTarget)
    setIsUploading(true)
    setImportError(null)

    try {
      const importedProject = await importProjectArchive(
        selectedArchive,
        formData.get('includeTests') === 'on',
        formData.get('sandboxEnabled') === 'on',
      )
      setProject((current) => current && {
        ...current,
        name: importedProject.projectName,
        source: `Upload accepted · ${selectedArchive.name}`,
      })
      setIsImporting(false)
      setActiveView('project')
      setHighlightedSection('project-files')
      window.setTimeout(() => setHighlightedSection(null), 1400)
    } catch (error) {
      setImportError(error instanceof Error ? error.message : 'Project import failed.')
    } finally {
      setIsUploading(false)
    }
  }

  return (
    <div className="workspace">
      <Sidebar activeView={activeView} onViewChange={navigateTo} />
      <main className="main-content">
        <header className="topbar">
          <div className="breadcrumb"><span>Workspaces</span><ChevronDown size={15} /><strong>AI Project Reviewer</strong></div>
          <div className="topbar-actions"><button className="icon-button" aria-label="Notifications" type="button"><Bell size={19} /></button><button className="import-button" onClick={() => setIsImporting(true)} type="button"><FolderUp size={17} /> Import project</button></div>
        </header>

        <div className="content-wrap" id="overview">
          <section className={highlightedSection === 'overview' ? 'page-intro section-highlight' : 'page-intro'}>
            <div><p className="eyebrow">{activeView === 'overview' ? 'Review workspace' : activeView}</p><h1>{project?.name ?? 'Loading project...'}</h1><p className="project-source">{project?.source}</p></div>
            <div className="analysis-actions"><span className={isRunning ? 'live-status running' : 'live-status'}><i />{isRunning ? 'Analysis in progress' : 'Last run 12 min ago'}</span><button className="primary-button" disabled={isRunning} onClick={() => void startAnalysis()} type="button"><Play size={16} fill="currentColor" />{isRunning ? 'Running analysis' : 'Run analysis'}</button></div>
          </section>

          <section className="metrics-grid" aria-label="Project metrics">
            <MetricCard detail="of source analyzed" label="Analysis coverage" value={`${project?.coverage ?? 0}%`} />
            <MetricCard detail="2 require attention" label="Open findings" tone="coral" value={String(project?.findings.length ?? 0)} />
            <MetricCard detail="Quality baseline: 75" label="Project score" tone="gold" value={`${project?.score ?? 0}/100`} />
          </section>

          <section className="review-grid">
            <div className="left-column">
              <section className={highlightedSection === 'analysis' ? 'panel analysis-panel section-highlight' : 'panel analysis-panel'} id="analysis" aria-labelledby="analysis-title">
                <div className="panel-heading"><div><p className="eyebrow">Evaluation plan</p><h2 id="analysis-title">Configured analyses</h2></div><button className="text-button" type="button">Edit configuration</button></div>
                <div className="analysis-list">
                  <label><input defaultChecked type="checkbox" /><span><strong>Architecture & maintainability</strong><small>Dependencies, layering, cohesion and coupling</small></span><ShieldCheck size={18} /></label>
                  <label><input defaultChecked type="checkbox" /><span><strong>Design pattern assessment</strong><small>Appropriateness and extension opportunities</small></span><Sparkles size={18} /></label>
                  <label><input defaultChecked type="checkbox" /><span><strong>LLM review</strong><small>GPT-4.1 · structured prompt · response validation</small></span><Sparkles size={18} /></label>
                </div>
                <div className="progress-block"><div><span>Pipeline status</span><strong>{isRunning ? 'Reviewing source files...' : 'Complete'}</strong></div><div className="progress-track"><span className={isRunning ? 'progress-fill running' : 'progress-fill'} /></div><small>{isRunning ? '67%' : '128 files processed · 3 responses validated'}</small></div>
              </section>
              {project && <FindingsTable findings={project.findings} onSelect={setSelectedFinding} />}
            </div>
            <div className="right-column">
              <div className={highlightedSection === 'project-files' ? 'section-highlight' : ''} id="project-files">{project && <ProjectTree files={project.files} />}</div>
              <section className={highlightedSection === 'report' ? 'panel report-panel section-highlight' : 'panel report-panel'} id="report" aria-labelledby="report-title"><div className="report-icon"><Download size={20} /></div><div><p className="eyebrow">Generated report</p><h2 id="report-title">Review report</h2><p>Structured evidence, scores, methodology, and recommendations.</p></div><button className="secondary-button" disabled={reportState === 'generating'} onClick={() => void exportReport()} type="button"><Download size={15} /> {reportState === 'generating' ? 'Generating PDF' : 'Download PDF'}</button><small>{reportState === 'ready' ? 'PDF report downloaded' : 'LaTeX content is converted into PDF on download'}</small></section>
            </div>
          </section>
        </div>
      </main>
      {selectedFinding && <div className="detail-backdrop" role="presentation" onClick={() => setSelectedFinding(null)}><article className="finding-detail" aria-label="Finding details" onClick={(event) => event.stopPropagation()}><button className="close-button" onClick={() => setSelectedFinding(null)} type="button">Close</button><span className={`severity ${selectedFinding.severity}`}>{selectedFinding.severity}</span><h2>{selectedFinding.title}</h2><p>{selectedFinding.description}</p><dl><div><dt>Source</dt><dd>{selectedFinding.source}</dd></div><div><dt>Score</dt><dd>{selectedFinding.score}/100</dd></div><div><dt>Evidence</dt><dd>{selectedFinding.id}</dd></div></dl></article></div>}
      {isImporting && <div className="detail-backdrop" role="presentation" onClick={() => setIsImporting(false)}><form className="import-dialog" aria-label="Import project" onClick={(event) => event.stopPropagation()} onSubmit={(event) => void importProject(event)}><button className="close-button" onClick={() => setIsImporting(false)} type="button">Close</button><p className="eyebrow">New evaluation</p><h2>Import a project</h2><p>Select a local project archive to upload for a secure backend evaluation.</p><label>Project archive<input accept=".zip,.tar,.gz" name="archive" onChange={selectArchive} ref={fileInputRef} type="file" /><small>{selectedArchive ? selectedArchive.name : 'No archive selected'}</small></label><div className="import-options"><label><input defaultChecked name="includeTests" type="checkbox" /> Include test files</label><label><input defaultChecked name="sandboxEnabled" type="checkbox" /> Apply secure sandbox</label></div>{importError && <p className="form-error" role="alert">{importError}</p>}<button className="primary-button" disabled={isUploading} type="submit"><FolderUp size={16} /> {isUploading ? 'Uploading archive' : selectedArchive ? 'Upload project' : 'Choose archive'}</button></form></div>}
    </div>
  )
}

export default App
