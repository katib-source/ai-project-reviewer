import { useEffect, useRef, useState } from 'react'
import { Bell, ChevronDown, Download, FolderUp, LoaderCircle, Play, ShieldCheck, Sparkles } from 'lucide-react'
import './App.css'
import { FindingsTable } from './components/FindingsTable'
import { ImportResult, type ImportOutcome } from './components/ImportResult'
import { MetricCard } from './components/MetricCard'
import { ProjectTree } from './components/ProjectTree'
import { Sidebar, type WorkspaceView } from './components/Sidebar'
import { importProject, selectUploadableFiles, uploadLimitProblem, uploadProjectFolder, type ImportedProject } from './services/projectImportService'
import { downloadRemoteReport } from './services/reportExportService'
import {
  flattenProjectTree,
  generateReport,
  getAnalysis,
  getAnalysisHistory,
  getCriteria,
  openAnalysisEvents,
  startAnalysis,
  type AnalysisHistoryItem,
  type Criterion,
  type Finding,
  type ReviewProject,
} from './services/reviewService'

function App() {
  const [activeView, setActiveView] = useState<WorkspaceView>('overview')
  const [project, setProject] = useState<ReviewProject | null>(null)
  const [criteria, setCriteria] = useState<Criterion[]>([])
  const [selectedCriterionIds, setSelectedCriterionIds] = useState<string[]>([])
  const [history, setHistory] = useState<AnalysisHistoryItem[]>([])
  const [analysisId, setAnalysisId] = useState<string | null>(null)
  const [isRunning, setIsRunning] = useState(false)
  const [progressText, setProgressText] = useState('Ready to analyze')
  const [selectedFinding, setSelectedFinding] = useState<Finding | null>(null)
  const [reportState, setReportState] = useState<'idle' | 'generating' | 'ready'>('idle')
  const [isImporting, setIsImporting] = useState(false)
  const [projectPath, setProjectPath] = useState('')
  const [importProgress, setImportProgress] = useState<string | null>(null)
  const [importOutcome, setImportOutcome] = useState<ImportOutcome | null>(null)
  const [error, setError] = useState<string | null>(null)
  const eventSourceRef = useRef<EventSource | null>(null)
  const folderInputRef = useRef<HTMLInputElement | null>(null)

  // The folder input only exists while the import dialog is open.
  useEffect(() => {
    const input = folderInputRef.current
    if (!input) return
    // Missing from React's typings: turns the file picker into a folder picker.
    input.setAttribute('webkitdirectory', '')
    // React has no onCancel for inputs; without this, closing the picker would leave the
    // "Reading folder..." state (and the locked button) in place.
    const onCancel = () => setImportProgress(null)
    input.addEventListener('cancel', onCancel)
    return () => input.removeEventListener('cancel', onCancel)
  }, [isImporting])

  const chooseFolder = () => {
    setError(null)
    // Browsers can take a long time to read a big folder before the page sees it: show it,
    // and keep the button locked so a second click cannot reopen the picker meanwhile.
    setImportProgress('Reading folder... big folders can take a while')
    folderInputRef.current?.click()
  }

  const openImportDialog = () => {
    setError(null)
    setImportProgress(null)
    setImportOutcome(null)
    setIsImporting(true)
  }

  useEffect(() => {
    void Promise.all([getCriteria(), getAnalysisHistory()])
      .then(([loadedCriteria, loadedHistory]) => {
        setCriteria(loadedCriteria)
        setSelectedCriterionIds(loadedCriteria.map((criterion) => criterion.id))
        setHistory(loadedHistory)
      })
      .catch((reason: unknown) => setError(reason instanceof Error ? reason.message : 'Unable to load backend data.'))

    return () => eventSourceRef.current?.close()
  }, [])

  const startReview = async () => {
    if (!project || selectedCriterionIds.length === 0) {
      setError('Import a project and select at least one criterion before starting analysis.')
      return
    }

    setError(null)
    setIsRunning(true)
    setProgressText('Starting analysis...')
    try {
      const started = await startAnalysis(project.id, selectedCriterionIds)
      setAnalysisId(started.analysisId)
      eventSourceRef.current?.close()
      eventSourceRef.current = openAnalysisEvents(started.analysisId, {
        onStarted: (event) => setProgressText(`${event.criterionName ?? 'Criterion'} started`),
        onCompleted: (event) => {
          if (event.overallScore === undefined) {
            setProgressText(`${event.criterionName ?? 'Criterion'} completed`)
            return
          }

          void getAnalysis(started.analysisId).then((result) => {
            setProject((current) => current ? {
              ...current,
              score: result.overallScore,
              findings: result.criteria.map((criterion) => ({
                id: criterion.criterionId,
                title: criterion.criterionName,
                description: criterion.comment,
                severity: criterion.score < 60 ? 'critical' : criterion.score < 80 ? 'warning' : 'info',
                source: criterion.criterionName,
                score: criterion.score,
              })),
            } : current)
            setIsRunning(false)
            setProgressText('Analysis complete')
            eventSourceRef.current?.close()
          }).catch((reason: unknown) => {
            setError(reason instanceof Error ? reason.message : 'Unable to load analysis results.')
            setIsRunning(false)
          })
        },
        onError: (event) => {
          setError(event.message ?? 'Analysis stream failed.')
          setIsRunning(false)
          eventSourceRef.current?.close()
        },
      })
    } catch (reason) {
      setError(reason instanceof Error ? reason.message : 'Unable to start analysis.')
      setIsRunning(false)
    }
  }

  const exportReport = async () => {
    if (!analysisId) {
      setError('Run an analysis before generating a report.')
      return
    }

    setReportState('generating')
    setError(null)
    try {
      const report = await generateReport(analysisId)
      const reportPath = report.pdfUrl || report.texUrl
      const extension = report.pdfUrl ? 'pdf' : 'tex'
      await downloadRemoteReport(reportPath, `analysis-${analysisId}.${extension}`)
      setReportState('ready')
    } catch (reason) {
      setError(reason instanceof Error ? reason.message : 'Unable to generate report.')
      setReportState('idle')
    }
  }

  const runImport = async (source: string, progressMessage: string, request: () => Promise<ImportedProject>) => {
    setError(null)
    setImportOutcome(null)
    setImportProgress(progressMessage)
    try {
      const imported = await request()
      setProject({
        id: imported.projectId,
        name: imported.tree.name,
        source,
        files: flattenProjectTree(imported.tree),
        findings: [],
        coverage: 0,
        score: 0,
      })
      setAnalysisId(null)
      setReportState('idle')
      setImportOutcome({ kind: 'success', title: 'Project imported', detail: `${source} is ready for analysis.` })
      // Let the success animation play before revealing the project.
      window.setTimeout(() => {
        setIsImporting(false)
        setImportOutcome(null)
        setActiveView('project')
      }, 1600)
    } catch (reason) {
      setImportOutcome({
        kind: 'failure',
        title: 'Import failed',
        detail: reason instanceof Error ? reason.message : 'Project import failed.',
      })
    } finally {
      setImportProgress(null)
    }
  }

  const importProjectByPath = async (event: React.FormEvent<HTMLFormElement>) => {
    event.preventDefault()
    const path = projectPath.trim()
    await runImport(path, 'Importing project...', () => importProject(path))
  }

  const uploadChosenFolder = async (event: React.ChangeEvent<HTMLInputElement>) => {
    // Copy first: resetting the input below empties its FileList in place.
    const chosen = Array.from(event.target.files ?? [])
    // Reset so picking the same folder again still fires a change event.
    event.target.value = ''
    if (chosen.length === 0) {
      setImportProgress(null)
      return
    }
    const files = selectUploadableFiles(chosen)
    const problem = files.length === 0
      ? 'That folder has no files to analyze (dependency and build folders, and files over 1 MB, are skipped).'
      : uploadLimitProblem(files)
    if (problem) {
      setImportProgress(null)
      setImportOutcome({ kind: 'failure', title: 'Cannot upload this folder', detail: problem })
      return
    }
    const folderName = files[0].webkitRelativePath.split('/')[0] || 'folder'
    const count = `${files.length.toLocaleString()} files`
    await runImport(`${folderName} (${count} uploaded)`, `Uploading ${count}...`,
      () => uploadProjectFolder(files, (fraction) => setImportProgress(fraction < 1
        ? `Uploading ${count}... ${Math.round(fraction * 100)}%`
        : 'Upload complete, importing project...')))
  }

  const navigateTo = (view: WorkspaceView) => {
    setActiveView(view)
    const sectionId = view === 'project' ? 'project-files' : view === 'analysis' ? 'analysis' : view === 'reports' ? 'report' : 'overview'
    document.getElementById(sectionId)?.scrollIntoView({ behavior: 'smooth', block: 'center' })
  }

  return (
    <div className="workspace">
      <Sidebar activeView={activeView} onViewChange={navigateTo} />
      <main className="main-content">
        <header className="topbar">
          <div className="breadcrumb"><span>Workspaces</span><ChevronDown size={15} /><strong>AI Project Reviewer</strong></div>
          <div className="topbar-actions"><button className="icon-button" aria-label="Notifications" type="button"><Bell size={19} /></button><button className="import-button" onClick={openImportDialog} type="button"><FolderUp size={17} /> Import project</button></div>
        </header>

        <div className="content-wrap" id="overview">
          {error && <p className="form-error" role="alert">{error}</p>}
          <section className="page-intro">
            <div><p className="eyebrow">{activeView === 'overview' ? 'Review workspace' : activeView}</p><h1>{project?.name ?? 'No project imported'}</h1><p className="project-source">{project?.source ?? 'Import a local project path to begin.'}</p></div>
            <div className="analysis-actions"><span className={isRunning ? 'live-status running' : 'live-status'}><i />{isRunning ? progressText : analysisId ? 'Analysis ready' : 'Waiting for project'}</span><button aria-busy={isRunning} className="primary-button" disabled={isRunning || !project} onClick={() => void startReview()} type="button"><Play size={16} fill="currentColor" />{isRunning ? 'Running analysis' : 'Run analysis'}</button></div>
          </section>

          <section className="metrics-grid" aria-label="Project metrics">
            <MetricCard detail="from imported project tree" label="Analysis coverage" value={`${project?.coverage ?? 0}%`} />
            <MetricCard detail="from latest analysis" label="Open findings" tone="coral" value={String(project?.findings.length ?? 0)} />
            <MetricCard detail={`${history.length} analyses in history`} label="Project score" tone="gold" value={`${project?.score ?? 0}/100`} />
          </section>

          <section className="review-grid">
            <div className="left-column">
              <section className="panel analysis-panel" id="analysis" aria-labelledby="analysis-title">
                <div className="panel-heading"><div><p className="eyebrow">Evaluation plan</p><h2 id="analysis-title">Available criteria</h2></div><span>{selectedCriterionIds.length} selected</span></div>
                <div className="analysis-list">
                  {criteria.map((criterion) => <label key={criterion.id}><input checked={selectedCriterionIds.includes(criterion.id)} onChange={(event) => setSelectedCriterionIds((current) => event.target.checked ? [...current, criterion.id] : current.filter((id) => id !== criterion.id))} type="checkbox" /><span><strong>{criterion.name}</strong><small>{criterion.description}</small></span>{criterion.weight >= 1 ? <ShieldCheck size={18} /> : <Sparkles size={18} />}</label>)}
                </div>
                <div className="progress-block"><div><span>Pipeline status</span><strong>{isRunning ? progressText : analysisId ? 'Complete' : 'Not started'}</strong></div><div className="progress-track"><span className={isRunning ? 'progress-fill running' : 'progress-fill'} /></div><small>{criteria.length} criteria available</small></div>
              </section>
              {project && <FindingsTable findings={project.findings} onSelect={setSelectedFinding} />}
            </div>
            <div className="right-column">
              <div id="project-files">{project && <ProjectTree files={project.files} />}</div>
              <section className="panel report-panel" id="report" aria-labelledby="report-title"><div className="report-icon"><Download size={20} /></div><div><p className="eyebrow">Generated report</p><h2 id="report-title">Review report</h2><p>Generate a report from the completed backend analysis.</p></div><button className="secondary-button" disabled={reportState === 'generating' || !analysisId} onClick={() => void exportReport()} type="button"><Download size={15} /> {reportState === 'generating' ? 'Generating report' : 'Download report'}</button><small>{reportState === 'ready' ? 'Report downloaded' : analysisId ? 'PDF or LaTeX, depending on backend availability' : 'Complete an analysis first'}</small></section>
            </div>
          </section>
        </div>
      </main>
      {selectedFinding && <div className="detail-backdrop" role="presentation" onClick={() => setSelectedFinding(null)}><article className="finding-detail" aria-label="Finding details" onClick={(event) => event.stopPropagation()}><button className="close-button" onClick={() => setSelectedFinding(null)} type="button">Close</button><span className={`severity ${selectedFinding.severity}`}>{selectedFinding.severity}</span><h2>{selectedFinding.title}</h2><p>{selectedFinding.description}</p><dl><div><dt>Source</dt><dd>{selectedFinding.source}</dd></div><div><dt>Score</dt><dd>{selectedFinding.score}/100</dd></div><div><dt>Evidence</dt><dd>{selectedFinding.id}</dd></div></dl></article></div>}
      {isImporting && <div className="detail-backdrop" role="presentation" onClick={() => setIsImporting(false)}><form className="import-dialog" aria-busy={importProgress !== null} aria-label="Import project" onClick={(event) => event.stopPropagation()} onSubmit={(event) => void importProjectByPath(event)}>
        <button className="close-button" onClick={() => setIsImporting(false)} type="button">Close</button>
        <p className="eyebrow">New evaluation</p>
        <h2>Import a project</h2>
        <p>Choose a project folder on your computer. Dependency and build folders (node_modules, .git, target...) are skipped.</p>
        <input hidden multiple onChange={(event) => void uploadChosenFolder(event)} ref={folderInputRef} type="file" />
        <button aria-busy={importProgress !== null} autoFocus className="primary-button" disabled={importProgress !== null} onClick={chooseFolder} type="button">
          {importProgress
            ? <><LoaderCircle className="spin" size={16} /> {importProgress}</>
            : <><FolderUp size={16} /> Choose folder...</>}
        </button>
        <p className="import-divider"><span>or enter a path on the backend's machine</span></p>
        <label>Project path<input name="path" onChange={(event) => setProjectPath(event.target.value)} placeholder="/home/you/my-project  or  C:\Users\you\my-project" type="text" value={projectPath} /></label>
        <button className="secondary-button" disabled={!projectPath.trim() || importProgress !== null} type="submit">Import from path</button>
        {importOutcome && <ImportResult onRetry={() => setImportOutcome(null)} outcome={importOutcome} />}
      </form></div>}
    </div>
  )
}

export default App